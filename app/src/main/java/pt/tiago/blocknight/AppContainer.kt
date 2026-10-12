package pt.tiago.blocknight

import android.content.Context
import java.io.File
import pt.tiago.blocknight.data.CampaignStore
import pt.tiago.blocknight.data.FreeStore
import pt.tiago.blocknight.data.ContentRepository
import pt.tiago.blocknight.data.ProfileStore

/** Dependências da app, criadas uma vez e passadas por construtor (PLANO §8: sem Hilt). */
class AppContainer(context: Context) {
    val content: ContentRepository by lazy { ContentRepository.load(context) }
    val profiles = ProfileStore(File(context.filesDir, "profile.json"))
    val campaign = CampaignStore(File(context.filesDir, "campaign.json"))
    /** The daily challenge keeps its own saved match, so it never replaces a saved campaign floor. */
    val daily = CampaignStore(File(context.filesDir, "daily.json"))
    val free = FreeStore(File(context.filesDir, "free.json"))
}
