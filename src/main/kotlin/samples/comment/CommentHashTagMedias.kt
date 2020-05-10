package samples.comment

import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username, password)
    bot.login()

    val hashTagName = "enter_hashtag_name_here"
    val commentText = "enter_your_comment_here"
    val howManyMediasYouWantToComment = 10

    bot.commentHashTagMedias(hashTagName, commentText, howManyMediasYouWantToComment).collect { println(it) }
}