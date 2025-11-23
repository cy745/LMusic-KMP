package com.lalilu.navigation

interface ScreenTransitionFactory {

    /**
     * 提供屏幕过渡动画的元数据信息
     *
     * @return 返回一个Map，其中key为String类型的标识符，value为Any类型的元数据对象
     */
    fun provideTransitionMetadata(): Map<String, Any>
}