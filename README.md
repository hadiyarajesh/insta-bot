# Instabot
Instagram bot implemented in Kotlin to perform all major operations supported by Instagram app.

## Features
- Like medias
- Comment medias
- Direct messages
- Watch stories
- Download medias
- Hashtag targeting
- Location targeting
- And more...

## Built with
[Kotlin](https://kotlinlang.org/) - A modern programming language for Android/JVM that makes developers happier.

[Coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html) - For asynchronous programming

[Flow](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/) - A cold asynchronous data stream that sequentially emits values and completes normally or with an exception.

[JsonPathKt](https://github.com/codeniko/JsonPathKt) - A lighter and more efficient implementation of JsonPath in Kotlin

## Installation

Add JitPack to your build.gradle.kts file
```kotlin
repositories {
    ...
    maven(url = "https://jitpack.io")
}
```

Add Gradle dependency to your build.gradle file
```kotlin
dependencies {
    implementation("com.github.hadiyarajesh:insta-bot:Tag")
}
```

## Quick start
Set your Instagram username and password in [Credentials.Kt](https://github.com/hadiyarajesh/insta-bot/blob/master/src/main/kotlin/Credentials.kt) file
```kotlin
object Credentials {
    const val USERNAME = "your_instagram_username"
    const val PASSWORD = "your_instagram_password"
}
```
Initialize InstagramBot class with credential value and call prepare method. Then, call login method to login into instagram. (Prepare method must be called before login method)
```kotlin
    val username = Credentials.USERNAME
    val password = Credentials.PASSWORD

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)
 ```

Now you can perform any operations of your choice like. 
```kotlin
// Get your own followers
bot.getSelfFollowing(Int.MAX_VALUE).collect { println(it) }
// Like 5 medias from explore page
bot.likeMediasByExplorePage(5).collect { println(it) }
// Approve all pending follow requests
bot.approveAllPendingFollowRequests().collect { println(it) }
// Watch stories of 200 users based on given location
bot.watchLocationUsersStories("enter_location_name_here", 200).collect { println(it) }
```

For more details, refer [BotTest](https://github.com/hadiarajesh/insta-bot/blob/master/src/main/kotlin/BotTest.kt) file.

## Samples
[You can find ready to use sample scripts here](https://github.com/hadiyarajesh/insta-bot/tree/master/src/main/kotlin/samples)

## Documentation
[You can find documentation here](https://hadiyarajesh.github.io/docs/instagram-api/index.html)

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## Terms and conditions
- You will NOT use this API for marketing purposes (spam, botting, harassment).
- We do NOT give support to anyone who wants to use this API to send spam or commit other crimes.
- We reserve the right to block any user of this repository that does not meet these conditions.

## Legal
This code is in no way affiliated with, authorized, maintained, sponsored or endorsed by Instagram, Facebook inc. or any of its affiliates or subsidiaries. This is an independent and unofficial API. Use it at your own risk.

## License
[MIT License](https://github.com/hadiyarajesh/insta-bot/blob/master/LICENSE)
