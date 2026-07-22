import Foundation
import MusicKit

// MARK: - ArtworkItem

@available(iOS 16.0, *)
@objc(ArtworkItem) public class ArtworkItem: NSObject {
    private let urlCallback: (Int, Int) -> URL?

    @objc public func url(withWidth width: Int, height: Int) -> URL? {
        return urlCallback(width, height)
    }

    init(urlCallback: @escaping (Int, Int) -> URL?) {
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
    private var pollingTimer: Timer?
    private var lastKnownStatus: MusicPlayer.PlaybackStatus?
    private var stateObservationTask: Task<Void, Never>?

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

    /// Play a single song identified by its store ID.
    /// The song must be in the cache (pre-configured via [configure]).
    @objc public func playWithStoreID(_ storeID: String) {
        guard let song = songCache[storeID] else {
            let error = NSError(
                domain: "MusicKitPlayerController",
                code: -2,
                userInfo: [NSLocalizedDescriptionKey: "Song not found in cache: \(storeID)"]
            )
            delegate?.onPlaybackError(error: error)
            return
        }

        stopPollingAndObservation()
        lastKnownStatus = nil

        stateObservationTask = Task { [weak self] in
            guard let self = self else { return }

            // Set the queue with just this song and start playback
            self.player.queue = [song]
            do {
                try await self.player.play()
                await self.startStateObservation()
            } catch {
                let nsError = error as NSError
                await MainActor.run {
                    self.delegate?.onPlaybackError(error: nsError)
                }
            }
        }
    }

    @objc public func resumePlayback() {
        stateObservationTask = Task { [weak self] in
            do {
                try await self?.player.play()
            } catch {
                let nsError = error as NSError
                await MainActor.run {
                    self?.delegate?.onPlaybackError(error: nsError)
                }
            }
        }
    }

    @objc public func pausePlayback() {
        player.pause()
    }

    @objc public func stopPlayback() {
        player.stop()
        stopPollingAndObservation()
    }

    @objc public func skipToNext() {
        Task { [weak self] in
            await self?.player.skipToNextEntry()
        }
    }

    @objc public func skipToPrevious() {
        Task { [weak self] in
            await self?.player.skipToPreviousEntry()
        }
    }

    @objc public func seekTo(_ time: Double) {
        player.state.playbackTime = time
    }

    // ── Synchronous State Queries ──

    @objc public var currentPlaybackTime: Double {
        player.state.playbackTime
    }

    @objc public var currentDuration: Double {
        player.state.currentEntry?.duration ?? 0
    }

    @objc public var isCurrentlyPlaying: Bool {
        player.state.playbackStatus == .playing
    }

    /// Release any running observation — call when switching to a different engine.
    @objc public func invalidate() {
        stopPollingAndObservation()
        songCache.removeAll()
    }

    // ── State Observation ──

    /// Observe state changes using MusicKit's async sequence.
    /// Runs until cancelled or the task is stopped.
    private func startStateObservation() async {
        stopPollingAndObservation()

        let task = Task { [weak self] in
            guard let self = self else { return }
            for await state in self.player.state {
                if Task.isCancelled { break }

                let status = state.playbackStatus
                let isPlaying = status == .playing

                await MainActor.run {
                    // Detect completion: transition from .playing to .stopped
                    if self.lastKnownStatus == .playing && status != .playing && status != .interrupted {
                        self.delegate?.onDidFinishPlaying()
                    }
                    self.lastKnownStatus = status

                    self.delegate?.onPlaybackStateChanged(
                        isPlaying: isPlaying,
                        playbackTime: state.playbackTime,
                        duration: state.currentEntry?.duration ?? 0
                    )
                }
            }
        }

        // Store reference so we can cancel later
        stateObservationTask = task
        _ = await task.value
    }

    private func stopPollingAndObservation() {
        pollingTimer?.invalidate()
        pollingTimer = nil
        stateObservationTask?.cancel()
        stateObservationTask = nil
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
                        ArtworkItem(urlCallback: { width, height in
                            artwork.url(width: width, height: height)
                        })
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
