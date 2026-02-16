package com.lalilu.navigation

interface ScreenMetadataFactory {

    /**
     * 提供Screen的元数据信息，存储的数据应该是不可变的
     *
     * @return 返回一个Map，其中key为String类型的标识符，value为Any类型的元数据对象
     */
    fun provideMetadata(): Map<String, Any>
}