package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.multiplayerscreens.MultiplayerHelpers
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.UncivSlider
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onChange
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton

class GameOptionsTable(
    private val previousScreen: IPreviousScreen,
    private val isPortrait: Boolean = false,
    private val updatePlayerPickerTable: (desiredCiv:String)->Unit
) : Table(BaseScreen.skin) {
    var gameParameters = previousScreen.gameSetupInfo.gameParameters
    val ruleset = previousScreen.ruleset
    var locked = false
    var modCheckboxes: ModCheckboxTable? = null
        private set
    // Remember this so we can unselect it when the pool dialog returns an empty pool
    private var randomNationsPoolCheckbox: CheckBox? = null

    init {
        getGameOptionsTable()
        background = BaseScreen.skinStrings.getUiBackground("NewGameScreen/GameOptionsTable", tintColor = BaseScreen.skinStrings.skinConfig.clearColor)
    }

    fun update() {
        clear()
        getGameOptionsTable()
    }

    private fun getGameOptionsTable() {
        top()
        defaults().pad(5f)

        // We assign this first to make sure addBaseRulesetSelectBox doesn't reference a null object
        modCheckboxes =
            if (isPortrait)
                getModCheckboxes(isPortrait = true)
            else getModCheckboxes()

        add(Table().apply {
            defaults().pad(5f)
            addBaseRulesetSelectBox()
            addDifficultySelectBox()
            addGameSpeedSelectBox()
            addEraSelectBox()
            // align left and right edges with other SelectBoxes but allow independent dropdown width
            add(Table().apply {
                val turnSlider = addMaxTurnsSlider()
                if (turnSlider != null)
                    add(turnSlider).padTop(10f).row()
                if (gameParameters.randomNumberOfPlayers) {
                    addMinPlayersSlider()
                    addMaxPlayersSlider()
                }
                if (gameParameters.randomNumberOfCityStates) {
                    addMinCityStatesSlider()
                    addMaxCityStatesSlider()
                } else {
                    addCityStatesSlider()
                }
            }).colspan(2).fillX().row()
        }).row()
        addVictoryTypeCheckboxes()

        val checkboxTable = Table().apply { defaults().left().pad(2.5f) }
        checkboxTable.addIsOnlineMultiplayerCheckbox()
        if (gameParameters.isOnlineMultiplayer)
            checkboxTable.addAnyoneCanSpectateCheckbox()
        add(checkboxTable).center().row()

        val expander = ExpanderTab(
            "Advanced Settings",
            startsOutOpened = gameParameters.enableRandomNationsPool,
            persistenceID = "GameOptionsTable.Advanced"
        ) {
            it.defaults().pad(5f, 0f)
            it.addNoCityRazingCheckbox()
            it.addNoBarbariansCheckbox()
            it.addRagingBarbariansCheckbox()
            it.addOneCityChallengeCheckbox()
            it.addNuclearWeaponsCheckbox()
            if (UncivGame.Current.settings.enableEspionageOption)
                it.addEnableEspionageCheckbox()
            it.addNoStartBiasCheckbox()
            it.addRandomPlayersCheckbox()
            it.addRandomCityStatesCheckbox()
            it.addRandomNationsPoolCheckbox()
            if (gameParameters.enableRandomNationsPool) {
                it.addNationsSelectTextButton()
            }
        }
        add(expander).pad(10f).row()

        if (!isPortrait)
            add(modCheckboxes).padTop(0f).row()

        pack()
    }

    private fun Table.addCheckbox(
        text: String,
        initialState: Boolean,
        lockable: Boolean = true,
        onChange: (newValue: Boolean) -> Unit
    ): CheckBox {
        val checkbox = text.toCheckBox(initialState) { onChange(it) }
        checkbox.isDisabled = lockable && locked
        checkbox.align(Align.left)
        add(checkbox).colspan(2).row()
        return checkbox
    }

    private fun Table.addNoCityRazingCheckbox() =
            addCheckbox("No City Razing", gameParameters.noCityRazing)
            { gameParameters.noCityRazing = it }

    private fun Table.addNoBarbariansCheckbox() =
            addCheckbox("No Barbarians", gameParameters.noBarbarians)
            { gameParameters.noBarbarians = it }

    private fun Table.addRagingBarbariansCheckbox() =
            addCheckbox("Raging Barbarians", gameParameters.ragingBarbarians)
            { gameParameters.ragingBarbarians = it }

    private fun Table.addOneCityChallengeCheckbox() =
            addCheckbox("One City Challenge", gameParameters.oneCityChallenge)
            { gameParameters.oneCityChallenge = it }

    private fun Table.addNuclearWeaponsCheckbox() =
            addCheckbox("Enable Nuclear Weapons", gameParameters.nuclearWeaponsEnabled)
            { gameParameters.nuclearWeaponsEnabled = it }

    private fun Table.addIsOnlineMultiplayerCheckbox() =
            addCheckbox("Online Multiplayer", gameParameters.isOnlineMultiplayer)
            { shouldUseMultiplayer ->
                gameParameters.isOnlineMultiplayer = shouldUseMultiplayer
                updatePlayerPickerTable("")
                if (shouldUseMultiplayer) {
                    MultiplayerHelpers.showDropboxWarning(previousScreen as BaseScreen)
                }
                update()
            }

    private fun Table.addAnyoneCanSpectateCheckbox() =
            addCheckbox("Allow anyone to spectate", gameParameters.anyoneCanSpectate)
            {
                gameParameters.anyoneCanSpectate = it
            }

    private fun Table.addEnableEspionageCheckbox() =
        addCheckbox("Enable Espionage", gameParameters.espionageEnabled)
        { gameParameters.espionageEnabled = it }

    private fun Table.addRandomNationsPoolCheckbox() {
        randomNationsPoolCheckbox = addCheckbox(
            "Set available nations for random pool",
            gameParameters.enableRandomNationsPool
        ) {
            gameParameters.enableRandomNationsPool = it
            update()  // To show the button opening the chooser popup
        }
    }

    private fun Table.addNationsSelectTextButton() {
        val button = "Select nations".toTextButton()
        button.onClick {
            val popup = RandomNationPickerPopup(previousScreen, gameParameters) {
                if (gameParameters.randomNationsPool.isEmpty()) {
                    gameParameters.enableRandomNationsPool = false
                    randomNationsPoolCheckbox?.isChecked = false
                }
            }
            popup.open()
            popup.update()
        }
        add(button)
    }

    private fun numberOfPlayable() = ruleset.nations.values.count {
        it.isMajorCiv()
    }

    private fun numberOfCityStates() = ruleset.nations.values.count {
        it.isCityState()
        && !it.hasUnique(UniqueType.CityStateDeprecated)
    }

    private fun Table.addNoStartBiasCheckbox() =
            addCheckbox("Disable starting bias", gameParameters.noStartBias)
            { gameParameters.noStartBias = it }

    private fun Table.addRandomPlayersCheckbox() =
            addCheckbox("Random number of Civilizations", gameParameters.randomNumberOfPlayers)
            {
                gameParameters.randomNumberOfPlayers = it
                update()  // To see the new sliders
            }

    private fun Table.addRandomCityStatesCheckbox() =
            addCheckbox("Random number of City-States", gameParameters.randomNumberOfCityStates)
            {
                gameParameters.randomNumberOfCityStates = it
                update()  // To see the changed sliders
            }

    private fun Table.addMinPlayersSlider() {
        val playableAvailable = numberOfPlayable()
        if (playableAvailable == 0) return

        add("{Min number of Civilizations}:".toLabel()).left().expandX()
        val slider = UncivSlider(2f, playableAvailable.toFloat(), 1f, initial = gameParameters.minNumberOfPlayers.toFloat()) {
            gameParameters.minNumberOfPlayers = it.toInt()
        }
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMaxPlayersSlider() {
        val playableAvailable = numberOfPlayable()
        if (playableAvailable == 0) return

        add("{Max number of Civilizations}:".toLabel()).left().expandX()
        val slider = UncivSlider(2f, playableAvailable.toFloat(), 1f, initial = gameParameters.maxNumberOfPlayers.toFloat()) {
            gameParameters.maxNumberOfPlayers = it.toInt()
        }
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMinCityStatesSlider() {
        val cityStatesAvailable = numberOfCityStates()
        if (cityStatesAvailable == 0) return

        add("{Min number of City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, cityStatesAvailable.toFloat(), 1f, initial = gameParameters.minNumberOfCityStates.toFloat()) {
            gameParameters.minNumberOfCityStates = it.toInt()
        }
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMaxCityStatesSlider() {
        val cityStatesAvailable = numberOfCityStates()
        if (cityStatesAvailable == 0) return

        add("{Max number of City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, cityStatesAvailable.toFloat(), 1f, initial = gameParameters.maxNumberOfCityStates.toFloat()) {
            gameParameters.maxNumberOfCityStates = it.toInt()
        }
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addCityStatesSlider() {
        val cityStatesAvailable = numberOfCityStates()
        if (cityStatesAvailable == 0) return

        add("{City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, cityStatesAvailable.toFloat(), 1f, initial = gameParameters.numberOfCityStates.toFloat()) {
            gameParameters.numberOfCityStates = it.toInt()
        }
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMaxTurnsSlider(): UncivSlider? {
        if (gameParameters.victoryTypes.none { ruleset.victories[it]?.enablesMaxTurns() == true })
            return null

        add("{Max Turns}:".toLabel()).left().expandX()
        val slider = UncivSlider(100f, 1500f, 5f, initial = gameParameters.maxTurns.toFloat()) {
            gameParameters.maxTurns = it.toInt()
        }
        slider.isDisabled = locked
        val snapValues = floatArrayOf(100f,150f,200f,250f,300f,350f,400f,450f,500f,550f,600f,650f,700f,750f,800f,900f,1000f,1250f,1500f)
        slider.setSnapToValues(snapValues, 125f)
        return slider
    }

    private fun Table.addSelectBox(text: String, values: Collection<String>, initialState: String, onChange: (newValue: String) -> String?) {
        add(text.toLabel()).left()
        val selectBox = TranslatedSelectBox(values, initialState, BaseScreen.skin)
        selectBox.isDisabled = locked
        selectBox.onChange {
            val changedValue = onChange(selectBox.selected.value)
            if (changedValue != null) selectBox.setSelected(changedValue)
        }
        onChange(selectBox.selected.value)
        add(selectBox).fillX().row()
    }

    private fun Table.addDifficultySelectBox() {
        addSelectBox("{Difficulty}:", ruleset.difficulties.keys, gameParameters.difficulty)
        { gameParameters.difficulty = it; null }
    }

    private fun Table.addBaseRulesetSelectBox() {
        val sortedBaseRulesets = RulesetCache.getSortedBaseRulesets()
        if (sortedBaseRulesets.size < 2) return

        addSelectBox(
            "{Base Ruleset}:",
            sortedBaseRulesets,
            gameParameters.baseRuleset
        ) { newBaseRuleset ->
            val previousSelection = gameParameters.baseRuleset
            if (newBaseRuleset == gameParameters.baseRuleset) return@addSelectBox null

            // Check if this mod is well-defined
            val baseRulesetErrors = RulesetCache[newBaseRuleset]!!.checkModLinks()
            if (baseRulesetErrors.isError()) {
                val toastMessage = "The mod you selected is incorrectly defined!".tr() + "\n\n${baseRulesetErrors.getErrorText()}"
                ToastPopup(toastMessage, previousScreen as BaseScreen, 5000L)
                return@addSelectBox previousSelection
            }

            // If so, add it to the current ruleset
            gameParameters.baseRuleset = newBaseRuleset
            onChooseMod(newBaseRuleset)

            // Check if the ruleset in it's entirety is still well-defined
            val modLinkErrors = ruleset.checkModLinks()
            if (modLinkErrors.isError()) {
                gameParameters.mods.clear()
                reloadRuleset()
                val toastMessage =
                    "This base ruleset is not compatible with the previously selected\nextension mods. They have been disabled.".tr()
                ToastPopup(toastMessage, previousScreen as BaseScreen, 5000L)

                modCheckboxes!!.disableAllCheckboxes()
            } else if (modLinkErrors.isWarnUser()) {
                val toastMessage =
                    "{The mod combination you selected has problems.}\n{You can play it, but don't expect everything to work!}".tr() +
                    "\n\n${modLinkErrors.getErrorText()}"
                ToastPopup(toastMessage, previousScreen as BaseScreen, 5000L)
            }

            modCheckboxes!!.setBaseRuleset(newBaseRuleset)

            null
        }
    }

    private fun Table.addGameSpeedSelectBox() {
        addSelectBox("{Game Speed}:", ruleset.speeds.values.map { it.name }, gameParameters.speed)
        { gameParameters.speed = it; null }
    }

    private fun Table.addEraSelectBox() {
        if (ruleset.technologies.isEmpty()) return // mod with no techs
        val eras = ruleset.eras.keys
        addSelectBox("{Starting Era}:", eras, gameParameters.startingEra)
        { gameParameters.startingEra = it; null }
    }

    private fun addVictoryTypeCheckboxes() {
        add("{Victory Conditions}:".toLabel()).colspan(2).row()

        // Create a checkbox for each VictoryType existing
        val victoryConditionsTable = Table().apply { defaults().pad(5f) }
        for ((i, victoryType) in ruleset.victories.values.withIndex()) {
            val victoryCheckbox = victoryType.name.toCheckBox(gameParameters.victoryTypes.contains(victoryType.name)) {
                // If the checkbox is checked, adds the victoryTypes else remove it
                if (it) {
                    gameParameters.victoryTypes.add(victoryType.name)
                } else {
                    gameParameters.victoryTypes.remove(victoryType.name)
                }
                // show or hide the max turns select box
                if (victoryType.enablesMaxTurns())
                    update()
            }
            victoryCheckbox.name = victoryType.name
            victoryCheckbox.isDisabled = locked
            victoryConditionsTable.add(victoryCheckbox).left()
            if ((i + 1) % 2 == 0) victoryConditionsTable.row()
        }
        add(victoryConditionsTable).colspan(2).row()
    }

    private fun reloadRuleset() {
        ruleset.clear()
        val newRuleset = RulesetCache.getComplexRuleset(gameParameters)
        ruleset.add(newRuleset)
        ruleset.mods += gameParameters.baseRuleset
        ruleset.mods += gameParameters.mods
        ruleset.modOptions = newRuleset.modOptions

        ImageGetter.setNewRuleset(ruleset)
        UncivGame.Current.musicController.setModList(gameParameters.getModsAndBaseRuleset())
    }

    private fun getModCheckboxes(isPortrait: Boolean = false): ModCheckboxTable {
        return ModCheckboxTable(gameParameters.mods, gameParameters.baseRuleset, previousScreen as BaseScreen, isPortrait) {
            onChooseMod(it)
        }
    }

    private fun onChooseMod(mod: String) {
        val activeMods: LinkedHashSet<String> = LinkedHashSet(gameParameters.getModsAndBaseRuleset())
        UncivGame.Current.translations.translationActiveMods = activeMods
        reloadRuleset()
        update()

        var desiredCiv = ""
        if (gameParameters.mods.contains(mod)) {
            val modNations = RulesetCache[mod]?.nations?.values?.filter { it.isMajorCiv() }

            if (modNations != null && modNations.any())
                desiredCiv = modNations.random().name

            val music = UncivGame.Current.musicController
            if (!music.chooseTrack(mod, MusicMood.Theme, MusicTrackChooserFlags.setSelectNation) && desiredCiv.isNotEmpty())
                music.chooseTrack(desiredCiv, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSelectNation)
        }

        updatePlayerPickerTable(desiredCiv)
    }
}

private class RandomNationPickerPopup(
    previousScreen: IPreviousScreen,
    private val gameParameters: GameParameters,
    private val onExit: () -> Unit
) : Popup(previousScreen as BaseScreen) {
    companion object {
        // These are used for the Close/OK buttons in the lower left/right corners:
        const val buttonsCircleSize = 70f
        const val buttonsIconSize = 50f
        const val buttonsOffsetFromEdge = 5f
        val buttonsBackColor: Color = Color.BLACK.cpy().apply { a = 0.67f }
    }

    // This Popup's body has two halves of same size, either side by side or arranged vertically
    // depending on screen proportions - determine height for one of those
    private val isPortrait = (previousScreen as BaseScreen).isPortrait()
    private val civBlocksWidth = stageToShowOn.width / 3 - 5f
    private val partHeight = stageToShowOn.height * (if (isPortrait) 0.45f else 0.8f)
    /** Widget offering Nations for the Pool (those that would be excluded) goes on the left/top */
    private val availableNationsListTable = Table()
    private val availableNationsListScroll = AutoScrollPane(availableNationsListTable)
    /** Widget selecting Nations for the Pool (those that could be chosen for a random slot) goes on the right/bottom */
    private val selectedNationsListTable = Table()
    private val selectedNationsListScroll = AutoScrollPane(selectedNationsListTable)
    /** sorted list of all major nations as ready-made button */
    private val allNationTables: ArrayList<NationTable>
    /** backing for the left side - unchosen Nations */
    private var availableNations: MutableSet<String>
    /** backing for the right side - chosen Nations */
    private var selectedNations: MutableSet<String>

    init {
        val sortedNations = previousScreen.ruleset.nations.values
                .filter { it.isMajorCiv() }
                .sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.name.tr() })
        allNationTables = ArrayList(
            sortedNations.map { NationTable(it, civBlocksWidth, 0f) }  // no need for min height
        )
        availableNations = sortedNations.map { it.name }.toMutableSet()
        selectedNations = gameParameters.randomNationsPool.intersect(availableNations) as MutableSet<String>
        availableNations.removeAll(selectedNations)

        availableNationsListScroll.setOverscroll(false, false)
        // size below uses civBlocksWidth +10, because the nation table has a 5f pad, for a total of +10f
        add("Banned nations".tr())
        if (isPortrait) {
            row()
            add(availableNationsListScroll).size( civBlocksWidth + 10f, partHeight ).row()
            addSeparator()
            add("Available nations".tr()).row()
        } else {
            add("Available nations".tr()).row()
            add(availableNationsListScroll).size( civBlocksWidth + 10f, partHeight )
        }
        selectedNationsListScroll.setOverscroll(false, false)
        add(selectedNationsListScroll).size(civBlocksWidth + 10f, partHeight)

        update()
        pack()

        val closeButton = "OtherIcons/Close".toImageButton(Color.FIREBRICK)
        closeButton.onActivation { close() }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        closeButton.setPosition(buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(closeButton)

        val okButton = "OtherIcons/Checkmark".toImageButton(Color.LIME)
        okButton.onClick { returnSelected() }
        okButton.setPosition(innerTable.width - buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomRight)
        innerTable.addActor(okButton)

        val switchButton = "OtherIcons/NationSwap".toImageButton(Color.YELLOW)
        switchButton.onClick { switchAllNations() }
        switchButton.setPosition(innerTable.width / 2, buttonsOffsetFromEdge + 35, Align.center)
        innerTable.addActor(switchButton)

        selectedNationsListTable.touchable = Touchable.enabled
    }

    fun update() {
        updateNationListTable(availableNationsListTable, availableNations) {
            nation -> { addNationToPool(nation) }
        }
        updateNationListTable(selectedNationsListTable, selectedNations) {
            nation -> { removeNationFromPool(nation) }
        }
    }

    private fun updateNationListTable(table: Table, nations: Set<String>, actionFactory: (Nation)->(()->Unit) ) {
        for (child in table.children) { child.listeners.clear() }
        table.clear()

        for (nationTable in allNationTables) {
            if (nationTable.nation.name !in nations) continue
            nationTable.onClick(actionFactory(nationTable.nation))
            table.add(nationTable).row()
        }
    }

    private fun String.toImageButton(overColor: Color): Group {
        val style = ImageButton.ImageButtonStyle()
        val image = ImageGetter.getDrawable(this)
        style.imageUp = image
        style.imageOver = image.tint(overColor)
        val button = ImageButton(style)
        button.setSize(buttonsIconSize, buttonsIconSize)

        return button.surroundWithCircle(buttonsCircleSize, false, buttonsBackColor)
    }

    private fun addNationToPool(nation: Nation) {
        availableNations.remove(nation.name)
        selectedNations.add(nation.name)

        update()
    }

    private fun removeNationFromPool(nation: Nation) {
        availableNations.add(nation.name)
        selectedNations.remove(nation.name)

        update()
    }

    private fun returnSelected() {
        close()
        gameParameters.randomNationsPool = ArrayList(selectedNations)
        onExit()
    }

    private fun switchAllNations() {
        val tempNations = availableNations
        availableNations = selectedNations
        selectedNations = tempNations
        update()
    }
}
