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
#import <MediaPlayer/MediaPlayer.h> // 确保导入MediaPlayer框架

MPMediaItemArtwork* createMediaItemArtwork(
        NSData* bitmapData,
        size_t bitmapWidth,
        size_t bitmapHeight,
        size_t bitsPerPixel,
        size_t bitsPerComponent,
        size_t bytesPerRow,
        size_t bitmapInfoType
) {
    NSLog(@"[createMediaItemArtwork]: data length: %lu, %zu, %zu, %zu, %zu, %zu",
            (unsigned long)[bitmapData length], bitmapWidth, bitmapHeight,
            bitsPerPixel, bitsPerComponent, bytesPerRow);

    // 严格校验输入参数
    if (!bitmapData || [bitmapData length] == 0 ||
            bitmapWidth == 0 || bitmapHeight == 0 ||
            bitsPerPixel == 0 || bitsPerComponent == 0 ||
            bytesPerRow == 0) {
        NSLog(@"Invalid input parameters");
        return nil;
    }

    // 校验数据长度是否匹配（避免内存访问越界）
    size_t expectedDataSize = bytesPerRow * bitmapHeight;
    if ([bitmapData length] < expectedDataSize) {
        NSLog(@"Bitmap data is too small: expected %zu bytes, got %lu bytes",
                expectedDataSize, (unsigned long)[bitmapData length]);
        return nil;
    }
    
    CGBitmapInfo bitmapInfo = kCGBitmapByteOrder16Little;
    switch (bitmapInfoType) {
        case 0:
            bitmapInfo = kCGBitmapByteOrderDefault;
            break;
        case 1:
            bitmapInfo = kCGBitmapByteOrder16Big;
            break;
        case 2:
            bitmapInfo = kCGBitmapByteOrder32Big;
            break;
        case 3:
            bitmapInfo = kCGBitmapByteOrder16Little;
            break;
        case 4:
            bitmapInfo = kCGBitmapByteOrder32Little;
            break;
        case 5: // 添加一个新的选项用于 RGBA 格式
            bitmapInfo = kCGImageAlphaPremultipliedLast | kCGBitmapByteOrder32Big;
            break;
        case 6:
            bitmapInfo = kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little;
            break;
        default:
            break;
    }
    
    CGSize boundsSize = CGSizeMake((CGFloat)bitmapWidth, (CGFloat)bitmapHeight);

    MPMediaItemArtwork* artwork = [[MPMediaItemArtwork alloc] initWithBoundsSize:boundsSize
    requestHandler:^NSImage * _Nonnull(CGSize requestedSize) {
        // 1. 创建颜色空间（根据实际需求调整，这里假设为RGB）
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
        if (!colorSpace) {
            NSLog(@"Failed to create color space");
            return NSImage.new;
        }

        // 2. 创建数据提供者（关键修复：使用[bitmapData bytes]获取实际数据指针）
        const void* pixelData = [bitmapData bytes];
        CGDataProviderRef provider = CGDataProviderCreateWithData(
                NULL,
                pixelData,
                expectedDataSize,
                NULL
        );
        if (!provider) {
            NSLog(@"Failed to create CGDataProvider");
            CGColorSpaceRelease(colorSpace);
            return NSImage.new;
        }

        // 3. 创建CGImage（使用外部传入的bitmapInfo，灵活适配不同格式）
        CGImageRef cgImage = CGImageCreate(
                bitmapWidth,
                bitmapHeight,
                bitsPerComponent,
                bitsPerPixel,
                bytesPerRow,
                colorSpace,
                bitmapInfo,
                provider,
                NULL,
                NO,
                kCGRenderingIntentDefault
        );

        // 4. 释放临时资源（Core Foundation对象）
        CGDataProviderRelease(provider); // 修复：释放provider
        CGColorSpaceRelease(colorSpace);

        if (!cgImage) {
            NSLog(@"Failed to create CGImage");
            return NSImage.new; // 失败时返回nil，而非空图像
        }

        // 5. 转换为NSImage
        NSImage* image = [[NSImage alloc] initWithCGImage:cgImage size:requestedSize];
        CGImageRelease(cgImage); // 释放CGImage

        return image;
    }];

    return artwork;
}