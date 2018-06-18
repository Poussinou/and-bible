package net.bible.service.device.speak

import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import net.bible.android.control.speak.SpeakSettings

abstract class SpeakCommand {
    open fun copy(): SpeakCommand {
        return this
    }

    abstract fun speak(tts: TextToSpeech, utteranceId: String)
}

// TODO: it might be better to make this immutable.
class TextCommand(text: String) : SpeakCommand() {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    var text: String = text.trim()
        set(value) {
            field = value.trim()
        }

    override fun toString(): String {
        return "${super.toString()} $text";
    }

    override fun copy(): SpeakCommand {
        return TextCommand(text)
    }
}

abstract class EarconCommand(val earcon: String, val speakSettings: SpeakSettings): SpeakCommand() {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val eBundle = Bundle()
            eBundle.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.1f)
            eBundle.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            if (speakSettings.playEarCons && !earcon.isEmpty()) {
                tts.playEarcon(earcon, TextToSpeech.QUEUE_ADD, eBundle, utteranceId)
            }
            else {
                tts.playSilentUtterance(0, TextToSpeech.QUEUE_ADD, utteranceId)
            }
        }
    }
}

class PreBookChangeCommand(speakSettings: SpeakSettings): EarconCommand(TextToSpeechServiceManager.EARCON_PRE_BOOK_CHANGE, speakSettings)
class PreChapterChangeCommand(speakSettings: SpeakSettings): EarconCommand(TextToSpeechServiceManager.EARCON_PRE_CHAPTER_CHANGE, speakSettings)
class PreTitleCommand(speakSettings: SpeakSettings): EarconCommand(TextToSpeechServiceManager.EARCON_PRE_TITLE, speakSettings)

open class SilenceCommand(val enabled: Boolean=true) : SpeakCommand() {
    override fun speak(tts: TextToSpeech, utteranceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.playSilentUtterance(if (enabled) 500 else 0, TextToSpeech.QUEUE_ADD, utteranceId)
        }
    }
}

class ParagraphChangeCommand(speakSettings: SpeakSettings) : SilenceCommand(speakSettings.delayOnParagraphChanges)

class SpeakCommands: ArrayList<SpeakCommand>() {
    private val maxLength = TextToSpeech.getMaxSpeechInputLength()
    private val endsWithSentenceBreak = Regex("(.*)([.?!]+[`´”“\"']*\\W*)")

    fun copy(): SpeakCommands {
        val cmds = SpeakCommands()
        for(cmd in this) {
            cmds.add(cmd.copy())
        }
        return cmds
    }

    val endsSentence: Boolean
        get() {
            val lastCommand = try {this.last()} catch(e: NoSuchElementException) {null}
            if (lastCommand is TextCommand) {
                return lastCommand.text.matches(endsWithSentenceBreak)
            }
            return true
        }

    override fun add(index: Int, element: SpeakCommand) {
        val currentCmd =  try {this.get(index)} catch (e: IndexOutOfBoundsException) {null}
        if(element is TextCommand) {
            if(element.text.isEmpty())
                return

            if (currentCmd is TextCommand) {
                val newText = "${element.text} ${currentCmd.text}"
                if (newText.length > maxLength)
                    return super.add(index, element)
                else {
                    currentCmd.text = newText
                    return
                }
            }
            else {
                return super.add(index, element)
            }
        }
        else if(element is SilenceCommand && currentCmd is SilenceCommand && element.enabled && currentCmd.enabled) {
            return // Do not add another
        }
        else {
            return super.add(index, element)
        }
    }

    override fun add(element: SpeakCommand): Boolean {
        val lastCommand = try {this.last()} catch (e: NoSuchElementException) {null}
        if(element is TextCommand) {
            if(element.text.isEmpty())
                return false

            if(lastCommand is TextCommand) {
                val newText = "${lastCommand.text} ${element.text}"
                if (newText.length > maxLength)
                    return super.add(element)
                else {
                    lastCommand.text = newText
                    return true
                }
            }
            else {
                if(!element.text.isEmpty()) {
                    return super.add(element)
                }
                else {
                    return false;
                }
            }
        }
        else if(element is SilenceCommand && lastCommand is SilenceCommand) {
            return false // Do not add another
        }
        else {
            return super.add(element)
        }
    }

    override fun toString(): String {
        return this.joinToString(" ") { it.toString() }
    }

    override fun addAll(elements: Collection<SpeakCommand>): Boolean {
        for(e in elements) {
            add(e)
        }
        return true
    }

    private val splitIntoTwoSentences = Regex("(.*)([.?!]+[`´”“\"']*\\W*)(.+)")

    fun addUntilSentenceBreak(commands: ArrayList<SpeakCommand>, rest: ArrayList<SpeakCommand>) {
        var sentenceBreakFound = false
        for(cmd in commands) {
            if(sentenceBreakFound) {
                rest.add(cmd)
            }
            else if(cmd is TextCommand) {
                val text = cmd.text
                val match = splitIntoTwoSentences.matchEntire(text)
                if(match != null) {
                    val (part1, delimiters, part2) = match.destructured
                    this.add(TextCommand("$part1$delimiters"))
                    rest.add(TextCommand(part2))
                    sentenceBreakFound = true
                }
                else {
                    this.add(cmd)
                }
            }
            else {
                this.add(cmd)
            }
        }
    }
}
