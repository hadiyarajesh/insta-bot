package samples.dm

import bot.InstagramBot
import com.nfeld.jsonpathlite.extension.read
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
fun main() = runBlocking {

    val username = "your_instagram_username"
    val password = "your_instagram_password"

    val bot = InstagramBot()
    bot.prepare(username, password)
    bot.login()

    val hashTagName = "enter_hashtag_name_here"
    val textName = "enter_text_name_to_send_along_with_hashtag"
    val timelineUsers = mutableListOf<String>()
    bot.getTimelineUsers().collect {
        timelineUsers.add(it?.read<String>("$.username").toString())
    }
    
    bot.sendHashTagToUsersIndividually(hashTagName, timelineUsers, textName).collect { println(it) }
}