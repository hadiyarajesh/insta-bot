package samples.dm

import bot.InstagramBot
import com.nfeld.jsonpathlite.extension.read
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username)
    bot.login(username, password)

    val hashTagName = "enter_hashtag_name_here_without_#"
    val textName = "enter_text_name_to_send_along_with_hashtag"

    runBlocking {
        bot.sendDirectHashTagToUsers(bot.getUsersByTimeline().map { it.get("pk").toString() }.toList(),
            hashTagName, textName).collect { println(it) }
    }

}