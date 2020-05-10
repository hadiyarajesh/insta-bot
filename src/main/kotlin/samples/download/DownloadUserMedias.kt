package samples.download

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

    val user = "enter_username_here_whose_media_you_want_to_download"
    val howManyMediasYouWantToDownload = 10
    val doYouWantToSaveDescriptionOfMedias = false

    bot.downloadUserMedias(user, howManyMediasYouWantToDownload, doYouWantToSaveDescriptionOfMedias).collect {
        println(it)
    }
}