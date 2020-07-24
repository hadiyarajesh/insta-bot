package samples.like

import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    val howManyMediasYouWantToLike = 10

    runBlocking {
        bot.likeMediasByExplorePage(howManyMediasYouWantToLike).collect { println(it) }
    }
}