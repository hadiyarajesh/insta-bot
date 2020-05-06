# Instabot
Free Instagram bot implemented in Kotlin to perform all major operations supported by Instagram app. You can call this bot from Kotlin/Java.

This bot is inspired from [Instabot](https://github.com/instagrambot/instabot)

## Built with
[Kotlin](https://kotlinlang.org/) - A modern programming language for Android/JVM that makes developers happier.

[Coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html) - For asynchronous programming

[Flow](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/) - A cold asynchronous data stream that sequentially emits values and completes normally or with an exception.

[JsonPathLite](https://github.com/codeniko/JsonPathLite) - A lighter and more efficient implementation of JsonPath in Kotlin


## Quick start
Initialize InstagramBot class with your username and password and call prepare method. Then call login method to login into instagram. (Prepare method must be called before login)
```
    val username = "your_username"
    val password = "your_password"

    val bot = InstagramBot()
    bot.prepare(username, password)
    bot.login()
```
Now you can perform any operations of your choice. 
```
bot.getExploreTabMedias(7).collect { println(it) }
bot.likeHashTagMedias("cat", 5).collect { println(it) }
bot.commentHashTagMedias("cat", "This is an exmaple of nice comment",5).collect { println(it) }
```

For more details, refer [BotTest](https://github.com/hadiarajesh/insta-bot/blob/master/src/main/kotlin/BotTest.kt) file.

## Documentation
Coming soon...

## Terms and conditions
- You will NOT use this API for marketing purposes (spam, botting, harassment).
- We do NOT give support to anyone who wants to use this API to send spam or commit other crimes.
- We reserve the right to block any user of this repository that does not meet these conditions.

## Legal
This code is in no way affiliated with, authorized, maintained, sponsored or endorsed by Instagram, Facebook inc. or any of its affiliates or subsidiaries. This is an independent and unofficial API. Use it at your own risk.
