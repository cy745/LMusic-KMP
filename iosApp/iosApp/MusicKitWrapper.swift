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

    @objc public init(
        title: String,
        artist: String,
        album: String,
        duration: TimeInterval,
        artwork: ArtworkItem?,
        url: URL?
    ) {
        self.title = title
        self.artist = artist
        self.album = album
        self.duration = duration
        self.artwork = artwork
        self.url = url
    }
}

// MARK: - MusicKitWrapper

@available(iOS 16.0, *)
@objc(MusicKitWrapper) public class MusicKitWrapper: NSObject {

    /// Returns a greeting message to verify the wrapper is loaded
    @objc public class func helloWorld() -> String {
        return "HeLLo WorLd! MusicKitWrapper"
    }

    /// Fetches user library songs from MusicKit
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
                        url: song.url
                    )
                }
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
