import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all) // Compose has own keyboard handler
            .onOpenURL { url in
                if url.scheme?.lowercased() == "lmusic" {
                    _ = DeepLinkHandler.shared.handle(rawUrl: url.absoluteString)
                } else {
                    IosExternalFileHandler.shared.handle(url: url)
                }
            }
    }
}
