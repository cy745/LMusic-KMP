//
//  MediaArtwork.m
//  rococoa
//
//  Created by miku on 2025/8/14.
//
#import "MediaArtwork.h"
#import <CoreGraphics/CoreGraphics.h>
#import <CoreGraphics/CGDataProvider.h>
#import <AppKit/NSImage.h>

MPMediaItemArtwork* createMediaItemArtwork(
    NSData* bitmapData,
    size_t bitmapWidth,
    size_t bitmapHeight,
    size_t bitsPerPixel,
    size_t bitsPerComponent,
    size_t bytesPerRow
) {
    NSLog(@"[createMediaItemArtwork]: %p, %zu, %zu, %zu, %zu, %zu", bitmapData, bitmapWidth, bitmapHeight, bitsPerPixel, bytesPerRow, bitsPerComponent);

    if (!bitmapData || bitmapWidth == 0 || bitmapHeight == 0) {
        NSLog(@"Invalid bitmap data");
        return nil;
    }
    
    // 创建CGSize
    CGSize boundsSize = CGSizeMake((CGFloat) bitmapWidth, (CGFloat) bitmapHeight);
    
    
    // 创建MPMediaItemArtwork
    MPMediaItemArtwork* artwork = [[MPMediaItemArtwork alloc] initWithBoundsSize:boundsSize
                                                                   requestHandler:^NSImage * _Nonnull(CGSize requestedSize) {
        // 根据请求的尺寸创建CGImage
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        if (!colorSpace) {
            NSLog(@"Failed to create color space");
            return NSImage.new;
        }
        
        CGDataProviderRef provider = CGDataProviderCreateWithData(
            NULL,                          // 自定义数据（可选）
            bitmapData,                    // 像素数据指针
            bytesPerRow * bitmapHeight,    // 数据总字节数
            NULL                           // 数据释放回调（可选）
        );
        
        // 创建CGImage
        CGImageRef cgImage = CGImageCreate(
            bitmapWidth,
            bitmapHeight,
            bitsPerComponent,
            bitsPerPixel,
            bytesPerRow,
            colorSpace,
            kCGBitmapByteOrder16Little,
            provider,
            NULL,
            NO,
            kCGRenderingIntentDefault
        );
        
        CGColorSpaceRelease(colorSpace);
        
        if (!cgImage) {
            NSLog(@"Failed to create CGImage");
            return NSImage.new;
        }
        
        // 从CGImage创建UIImage
        NSImage* image = [[NSImage alloc] initWithCGImage:cgImage
                                            size:requestedSize];
        NSLog(@"NSImage created");
        
        CGImageRelease(cgImage);
        NSLog(@"CGImage released");
        return image;
    }];
    
    // 注意：这里我们没有使用originalArtworkId，
    // 如果需要使用它，可以根据需求添加相关逻辑
    
    return artwork;
}
