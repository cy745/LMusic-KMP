## Subsonic接口对接任务

请帮我实现Subsonic API的接口对接：我将给你提供一个接口和这个接口实际会输出的json格式示例，
请帮生成对应的kotlin的接口方法和对应数据类和对应的注释，项目使用Kotlin、Kotlin Serialization、Ktorfit实现，
已经实现了部分接口，请参照示例完成任务

**接口文档地址：** https://www.subsonic.org/pages/api.jsp

#### 需要注意的一些事项原则：

1. 为数据类的所有参数填充默认值
2. 当响应数据中某项元素值为null，导致无法获取类型信息时，请使用Any?类型
3. 注意data class必须要有参数，如果某个元素是空对象，则不应该使用data class定义，而应该直接使用class定义
4. 如果我没有提供json数据，则从接口文档中目标接口的Example链接中获取xml结构的响应示例数据，并以该数据来实现数据类

### 已经构建好的结构

```kotlin
// 外层数据类封装
@Serializable
data class SubsonicResponseWrapper<T : SubsonicResponse>(
    @SerialName("subsonic-response")
    val response: T
)

@Serializable
open class SubsonicResponse(
    val status: String = "",
    val version: String = "",
    val type: String = "",
    @SerialName("serverVersion")
    val serverVersion: String = "",
    @SerialName("openSubsonic")
    val openSubsonic: Boolean = true,
    val error: SubsonicError? = null
) {
    val isSuccess: Boolean = status == "ok"
    val isFailed: Boolean = status == "failed"
    val isError: Boolean = error != null
    val errorCode: Int = error?.code ?: 0
    val errorMessage: String = error?.message ?: ""
}

@Serializable
data class SubsonicError(
    val code: Int = 0,
    val message: String = ""
) {
    override fun toString(): String {
        return "[SubsonicError]: $code - $message"
    }
}

// 实际接口返回的子数据结构定义示例（注意添加注释）
@Serializable
data class GetMusicFoldersResponse(
    val musicFolders: MusicFolders
) : SubsonicResponse() {

    @Serializable
    data class MusicFolders(
        val musicFolder: List<MusicFolder>
    ) {

        @Serializable
        data class MusicFolder(
            val id: String,
            val name: String
        )
    }
}
```

```kotlin
// subsonic相关的配置参数通过Ktor的插件在请求时附加，无需在接口中定义
private val subsonicPlugin by lazy {
    createClientPlugin("SUBSONIC_PAYLOAD_PLUGIN") {
        onRequest { request, _ ->
            request.parameter("u", configItem.value.username)
            request.parameter("v", configItem.value.version)
            request.parameter("c", configItem.value.client)
            request.parameter("f", configItem.value.format)
            request.parameter("s", configItem.value.salt)
            request.parameter("t", configItem.value.token)
        }
    }
}
private val ktorClient by lazy {
    HttpClient {
        install(ContentNegotiation) { json(json) }
        install(subsonicPlugin)
    }
}
```

```kotlin
// 接口定义
interface SubsonicApi {
    /**
     * http://your-server/rest/ping Since [1.0.0](https://www.subsonic.org/pages/api.jsp#versions)
     *
     * Used to test connectivity with the server. Takes no extra parameters.
     * 用于测试服务器的 connectivity。无额外参数
     *
     * Returns an empty <subsonic-response> element on success.
     * 成功时返回一个空的 <subsonic-response> 元素。[Example](https://www.subsonic.org/pages/inc/api/examples/ping_example_1.xml)
     */
    @GET("ping")
    suspend fun ping(): SubsonicResponseWrapper<SubsonicResponse>
}
```

### 目标接口

接口名称：getAlbumInfo2

接口返回数据示例：

```json
```


