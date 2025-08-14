//
//  MediaArtwork.h
//  rococoa
//
//  Created by miku on 2025/8/14.
//
#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>
#import <CoreGraphics/CoreGraphics.h>


/**
 * 封装MPMediaItemArtwork的初始化方法，用于JNA映射
 *
 * @param bitmapData 位图数据字节数组
 * @param bitmapWidth 位图宽度
 * @param bitmapHeight 位图高度
 * @param bitsPerPixel 每像素位数
 * @param bitsPerComponent 每个颜色分量的位数
 * @param bytesPerRow 每行字节数
 *
 * @return 指向创建的MPMediaItemArtwork实例的指针
 */
MPMediaItemArtwork* createMediaItemArtwork(
       NSData* bitmapData,
       size_t bitmapWidth,
       size_t bitmapHeight,
       size_t bitsPerPixel,
       size_t bitsPerComponent,
       size_t bytesPerRow
);

