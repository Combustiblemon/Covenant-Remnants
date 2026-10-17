package org.combustiblelemon.covenantremnants.utils

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.codex.CodexDataV2
import com.fs.starfarer.api.impl.codex.CodexTextEntryLoader
import com.fs.starfarer.api.loading.Description
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc

/**
 * Codex-only lore (developer request 2026-10-17: Sector analyst voice).
 *
 * - [addHullmodLore]: hullmods call it from addPostDescriptionSection. It adds a paragraph only while
 *   the Codex renders an entry (Global.CODEX_TOOLTIP_MODE), so refit tooltips stay mechanical. The
 *   text is the CUSTOM descriptions.csv row "<hullmod id>_codex".
 * - [addArchiveEntries]: the "Covenant Archive" Codex category, from [ARCHIVE_FILE] in vanilla's
 *   Codex text markup. 0.98a ships its text-entry loader switched off
 *   (CodexDataV2.WITH_GAME_MECHANICS_CAT = false), so it never reads data/codex/text_codex_entries.csv;
 *   the mod plugin runs the same parser from ModPlugin.onAboutToLinkCodexEntries instead.
 */
object CodexLore {
    const val ARCHIVE_FILE = "data/codex/covenant_remnants_archive.txt"
    private const val HULLMOD_LORE_SUFFIX = "_codex"

    private val logger = Global.getLogger(CodexLore::class.java)

    fun addHullmodLore(tooltip: TooltipMakerAPI?, hullmodId: String) {
        if (tooltip == null || !Global.CODEX_TOOLTIP_MODE) return
        val desc = runCatching {
            Global.getSettings().getDescription(hullmodId + HULLMOD_LORE_SUFFIX, Description.Type.CUSTOM)
        }.getOrNull() ?: return
        if (!desc.hasText1()) return
        for (para in desc.text1Paras) {
            tooltip.addPara(para, Misc.getGrayColor(), 10f)
        }
    }

    /** Parses [ARCHIVE_FILE] into the Codex and links its related entries. Call once per Codex build. */
    fun addArchiveEntries() {
        try {
            val contents = Global.getSettings().loadText(ARCHIVE_FILE)
            CodexTextEntryLoader.parseContents(contents, ARCHIVE_FILE)
            // Text entries attach to their category but are not in the id map until it is rebuilt,
            // and the {{rel:...}} / RELATED links resolve through that map.
            CodexDataV2.rebuildIdToEntryMap()
            CodexTextEntryLoader.linkRelated()
        } catch (e: Exception) {
            logger.error("Covenant Codex archive not loaded from $ARCHIVE_FILE", e)
        }
    }
}
