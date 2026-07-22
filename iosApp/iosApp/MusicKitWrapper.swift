import Foundation
import MediaPlayer
import MusicKit

// MARK: - ArtworkItem

@available(iOS 16.0, *)
@objc(ArtworkItem) public class ArtworkItem: NSObject {
    private let urlCallback: (Int, Int) -> URL?

    @objc public let maxWidth: Int
    @objc public let maxHeight: Int

    @objc public func url(withWidth width: Int, height: Int) -> URL? {
        return urlCallback(width, height)
    }

    init(maxWidth: Int, maxHeight: Int, urlCallback: @escaping (Int, Int) -> URL?) {
        self.maxWidth = maxWidth
        self.maxHeight = maxHeight
        self.urlCallback = urlCallback
    }
}

// MARK: - SongInfo

@available(iOS 16.0, *)
@objc(SongInfo) public class SongInfo: NSObject {
    @objc public let title: String
    @objc public let artist: String
    @objc public let album: String
    @objc public let duration: TimeInterval
    @objc public let artwork: ArtworkItem?
    @objc public let url: URL?
    @objc public let storeID: String

    /// Internal reference to the MusicKit Song object, used for playback.
    internal let song: Song?

    @objc public init(
        title: String,
        artist: String,
        album: String,
        duration: TimeInterval,
        artwork: ArtworkItem?,
        url: URL?,
        storeID: String
    ) {
        self.title = title
        self.artist = artist
        self.album = album
        self.duration = duration
        self.artwork = artwork
        self.url = url
        self.storeID = storeID
        self.song = nil
    }

    /// Internal init that preserves the Song reference.
    internal init(
        title: String,
        artist: String,
        album: String,
        duration: TimeInterval,
        artwork: ArtworkItem?,
        url: URL?,
        storeID: String,
        song: Song?
    ) {
        self.title = title
        self.artist = artist
        self.album = album
        self.duration = duration
        self.artwork = artwork
        self.url = url
        self.storeID = storeID
        self.song = song
    }
}

// MARK: - MusicKitPlayerControllerDelegate

@available(iOS 16.0, *)
@objc(MusicKitPlayerControllerDelegate)
public protocol MusicKitPlayerControllerDelegate: NSObjectProtocol {
    /// Called periodically (~250ms) with current playback state.
    @objc func onPlaybackStateChanged(
        isPlaying: Bool,
        playbackTime: Double,
        duration: Double
    )

    /// Called when the current song finishes playing naturally.
    @objc func onDidFinishPlaying()

    /// Called on playback error.
    @objc func onPlaybackError(error: NSError)
}

// MARK: - MusicKitPlayerController

@available(iOS 16.0, *)
@objc(MusicKitPlayerController) public class MusicKitPlayerController: NSObject {

    /// Shared singleton controller that MusicKitEngine uses.
    @objc public static let shared = MusicKitPlayerController()

    @objc public weak var delegate: MusicKitPlayerControllerDelegate?

    private let player = ApplicationMusicPlayer.shared
    private var songCache: [String: Song] = [:]

    // ── Time Tracking ──
    /// When playback started/resumed; nil when paused.
    private var playStartTime: Date?
    /// Accumulated play time before the current play session (in seconds).
    private var accumulatedTime: TimeInterval = 0
    /// Duration of the currently loaded song (from SongInfo).
    private var currentSongDuration: TimeInterval = 0

    // ── Polling ──
    private var pollingTimer: Timer?
    /// Last known status for completion detection.
    private var lastKnownStatus: MusicKit.MusicPlayer.PlaybackStatus?

    // ── Cache Management ──

    /// Populate the song cache from a list of SongInfo objects.
    /// Call this after fetching songs; the cache is used by [playWithStoreID].
    @objc public func configure(with songInfos: [SongInfo]) {
        var cache: [String: Song] = [:]
        for info in songInfos {
            if let song = info.song {
                cache[info.storeID] = song
            }
        }
        songCache = cache
    }

    /// Number of songs currently in cache.
    @objc public var cachedSongCount: Int { songCache.count }

    // ── Playback Control ──

    /// Set queue to a single song identified by its store ID.
    /// Does NOT start playback — call [resumePlayback] separately.
    /// The song must be in the cache (pre-configured via [configure]).
    @objc public func setQueueWithStoreID(_ storeID: String) {
        guard let song = songCache[storeID] else {
            let error = NSError(
                domain: "MusicKitPlayerController",
                code: -2,
                userInfo: [NSLocalizedDescriptionKey: "Song not found in cache: \(storeID)"]
            )
            delegate?.onPlaybackError(error: error)
            return
        }

        stopPolling()

        // Reset time tracking
        accumulatedTime = 0
        playStartTime = nil
        currentSongDuration = song.duration ?? 0
        lastKnownStatus = nil

        // Queue the song only — playback will be started by resumePlayback
        player.queue = [song]
    }

    /// Start or resume playback of the currently queued song.
    @objc public func resumePlayback() {
        Task { [weak self] in
            guard let self = self else { return }
            do {
                try await self.player.play()
                await MainActor.run {
                    self.playStartTime = Date()
                    self.startPolling()
                }
            } catch {
                let nsError = error as NSError
                await MainActor.run {
                    self.delegate?.onPlaybackError(error: nsError)
                }
            }
        }
    }

    @objc public func pausePlayback() {
        player.pause()
        if let start = playStartTime {
            accumulatedTime += Date().timeIntervalSince(start)
        }
        playStartTime = nil
    }

    @objc public func stopPlayback() {
        player.stop()
        stopPolling()
        accumulatedTime = 0
        playStartTime = nil
        currentSongDuration = 0
        lastKnownStatus = nil
    }

    @objc public func skipToNext() {
        Task { [weak self] in
            try? await self?.player.skipToNextEntry()
        }
    }

    @objc public func skipToPrevious() {
        Task { [weak self] in
            try? await self?.player.skipToPreviousEntry()
        }
    }

    @objc public func seekTo(_ time: Double) {
        // MusicPlayer.playbackTime 是 MusicKit 官方 seek API，iOS 15.0+ 可用
        accumulatedTime = time
        playStartTime = Date()
        player.playbackTime = time
    }

    // ── Artwork Loading ──

    /// Fetch artwork image data for a song by store ID.
    /// Attempts to load the URL returned by MusicKit's Artwork API;
    /// returns nil if the URL is not loadable (e.g. musicKit:// scheme).
    @objc public func artworkDataForStoreID(_ storeID: String, width: Int, height: Int) -> NSData? {
        guard let song = songCache[storeID],
            let artwork = song.artwork,
            let url = artwork.url(width: width, height: height)
        else { return nil }

        guard let data = try? Data(contentsOf: url), !data.isEmpty else { return nil }
        return data as NSData
    }

    // ── Lyrics ──

    /// Retrieve lyrics for a song using its store ID via MPMediaItem lookup.
    @objc public func lyricsForStoreID(_ storeID: String) -> String? {
        guard let song = songCache[storeID] else { return nil }
        let idValue = UInt64(song.id.description) ?? 0
        guard idValue > 0 else { return nil }
        let predicate = MPMediaPropertyPredicate(
            value: idValue,
            forProperty: MPMediaItemPropertyPersistentID
        )
        let query = MPMediaQuery(filterPredicates: [predicate])
        return query.items?.first?.lyrics
    }

    // ── Synchronous State Queries ──

    @objc public var currentPlaybackTime: Double {
        player.playbackTime
    }

    @objc public var currentDuration: Double {
        currentSongDuration
    }

    @objc public var isCurrentlyPlaying: Bool {
        player.state.playbackStatus == .playing
    }

    /// Release any running observation — call when switching to a different engine.
    @objc public func invalidate() {
        stopPolling()
        accumulatedTime = 0
        playStartTime = nil
        currentSongDuration = 0
        lastKnownStatus = nil
        // 不清理 songCache——它是跨 Engine 生命周期共享的，由 configure() 管理
    }

    // ── Polling (state observation for iOS 16 compatibility) ──

    /// Poll player state every ~250ms and report via delegate.
    private func startPolling() {
        stopPolling()

        pollingTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] timer in
            guard let self = self else {
                timer.invalidate()
                return
            }
            self.pollState()
        }
    }

    private func pollState() {
        let status = player.state.playbackStatus
        let isPlaying = status == .playing
        let time = player.playbackTime

        // 检测完成：状态变到 .stopped，或播放位置到达结尾
        let reachedEnd = currentSongDuration > 0 && abs(time - currentSongDuration) < 1.5
        if reachedEnd && isPlaying {
            print("[MusicKitPlayerController] reached end of song (time=\(time) duration=\(currentSongDuration)) firing completion")
            delegate?.onDidFinishPlaying()
        } else if status == .stopped && lastKnownStatus != .stopped {
            print("[MusicKitPlayerController] status=stopped, firing completion")
            delegate?.onDidFinishPlaying()
        }

        lastKnownStatus = status

        delegate?.onPlaybackStateChanged(
            isPlaying: isPlaying,
            playbackTime: time,
            duration: currentSongDuration
        )
    }

    private func stopPolling() {
        pollingTimer?.invalidate()
        pollingTimer = nil
    }
}

// MARK: - MusicKitWrapper (extended)

@available(iOS 16.0, *)
@objc(MusicKitWrapper) public class MusicKitWrapper: NSObject {

    /// Returns a greeting message to verify the wrapper is loaded
    @objc public class func helloWorld() -> String {
        return "HeLLo WorLd! MusicKitWrapper"
    }

    /// Fetches user library songs from MusicKit.
    /// This method requests music authorization and fetches the user's library songs.
    /// The completion handler is called with:
    /// - songs: Array of SongInfo objects on success (may be empty), nil on failure
    /// - error: Error object on failure, nil on success
    @objc public class func fetchUserLibrarySongs(completionHandler: @escaping ([SongInfo]?, Error?) -> Void) {
        Task {
            guard await requestMusicAuthorization() else {
                print("音乐库访问授权被拒绝")
                completionHandler(
                    nil,
                    NSError(
                        domain: "MusicKitWrapper",
                        code: -1,
                        userInfo: [NSLocalizedDescriptionKey: "Music library access denied"]
                    )
                )
                return
            }

            var request = MusicLibraryRequest<Song>()
            request.sort(by: \.title, ascending: true)

            do {
                let response = try await request.response()
                let songs = response.items.map { song in
                    let artworkItem: ArtworkItem? = song.artwork.map { artwork in
                        ArtworkItem(
                            maxWidth: artwork.maximumWidth ?? 0,
                            maxHeight: artwork.maximumHeight ?? 0,
                            urlCallback: { width, height in
                                artwork.url(width: width, height: height)
                            }
                        )
                    }

                    return SongInfo(
                        title: song.title,
                        artist: song.artistName,
                        album: song.albumTitle ?? "未知专辑",
                        duration: song.duration ?? 0,
                        artwork: artworkItem,
                        url: song.url,
                        storeID: song.id.description,
                        song: song
                    )
                }
                // Auto-configure the shared player controller's song cache
                MusicKitPlayerController.shared.configure(with: songs)
                completionHandler(songs, nil)
            } catch {
                print("获取歌曲失败: \(error)")
                completionHandler(nil, error)
            }
        }
    }

    // MARK: - Private

    @available(iOS 16.0, *)
    private class func requestMusicAuthorization() async -> Bool {
        let status = await MusicAuthorization.request()
        return status == .authorized
    }
}
