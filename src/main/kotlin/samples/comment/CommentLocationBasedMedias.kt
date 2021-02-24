package samples.comment

import Credentials
import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = Credentials.USERNAME
    val password = Credentials.PASSWORD

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    val locationName = "enter_location_name_here"
    val commentList = listOf("Comment 1", "Comment 2")
    val howManyMediasYouWantToComment = 10

    bot.commentMediasByLocation(locationName, commentList, howManyMediasYouWantToComment).collect { println(it) }
}