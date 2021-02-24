package samples.dm

import Credentials
import bot.InstagramBot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = Credentials.USERNAME
    val password = Credentials.PASSWORD

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    val hashTagName = "enter_hashtag_name_here_without_#"
    val textName = "enter_text_name_to_send_along_with_hashtag"

    bot.sendDirectHashTagToUsers(
        bot.getUsersByTimeline().map { it.get("pk").toString() }.toList(),
        hashTagName, textName
    ).collect { println(it) }
}