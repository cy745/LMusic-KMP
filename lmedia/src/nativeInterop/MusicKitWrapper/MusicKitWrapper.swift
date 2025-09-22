
import Foundation
import MusicKit

@available(iOS 16.0, *)
@objc public class ArtworkItem: NSObject {
    private let urlCallback: (Int, Int) -> URL?

    @objc public func url(width: Int, height: Int) -> URL? {
        return urlCallback(width, height)
    }

    @available(iOS 16.0, *)
    init(urlCallback: @escaping (Int, Int) -> URL?) {
        self.urlCallback = urlCallback
    }
}

@available(iOS 16.0, *)
@objc public class SongInfo: NSObject {
    @objc public let title: String
    @objc public let artist: String
    @objc public let album: String
    @objc public let duration: TimeInterval
    @objc public let artwork: ArtworkItem?
    @objc public let url: URL?

    @available(iOS 16.0, *)
    init(title: String, artist: String, album: String, duration: TimeInterval, artwork: ArtworkItem?, url: URL?) {
        self.title = title
        self.artist = artist
        self.album = album
        self.duration = duration
        self.artwork = artwork
        self.url = url
    }
}

@available(iOS 16.0, *)
@objc public class MusicKitWrapper: NSObject {
    @objc public class func helloWorld() -> String {
        return "HeLLo WorLd! MusicKitWrapper"
    }

    /// 异步获取用户音乐库中的歌曲列表
    /// - Returns: 歌曲信息数组，获取失败时返回空数组
    @available(iOS 16.0, *)
    @objc public class func fetchUserLibrarySongs() async -> [SongInfo] {
        guard await requestMusicAuthorization() else {
            print("音乐库访问授权被拒绝")
            return []
        }

        var request = MusicLibraryRequest<Song>()
        request.sort(by: \.title, ascending: true) // 按标题排序

        do {
            let response = try await request.response()
            return response.items.map { song in
                let artworkItem: ArtworkItem? = song.artwork != nil ?
                    ArtworkItem(urlCallback: { (width, height) in
                        return song.artwork?.url(width: width, height: height)
                    }) : nil

                return SongInfo(
                    title: song.title,
                    artist: song.artistName,
                    album: song.albumTitle ?? "未知专辑",
                    duration: song.duration ?? 0,
                    artwork: artworkItem,
                    url: song.url
                )
            }
        } catch {
            print("获取歌曲失败: \(error)")
            return []
        }
    }

    @available(iOS 16.0, *)
    private class func requestMusicAuthorization() async -> Bool {
        let status = await MusicAuthorization.request()
        return status == .authorized
    }
}