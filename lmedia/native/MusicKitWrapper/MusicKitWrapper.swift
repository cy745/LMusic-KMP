import Foundation
import MusicKit

@objc public class SongInfo: NSObject {
    @objc public let title: String
    @objc public let artist: String
    @objc public let album: String
    @objc public let duration: TimeInterval

    init(title: String, artist: String, album: String, duration: TimeInterval) {
        self.title = title
        self.artist = artist
        self.album = album
        self.duration = duration
    }
}

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
                SongInfo(
                    title: song.title ?? "未知标题",
                    artist: song.artistName ?? "未知艺术家",
                    album: song.albumTitle ?? "未知专辑",
                    duration: song.duration ?? 0
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