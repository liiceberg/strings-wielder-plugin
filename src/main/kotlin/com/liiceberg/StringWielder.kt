package com.liiceberg

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbService
import com.liiceberg.module.ModuleAnalysisService


class StringWielder : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(PlatformDataKeys.PROJECT) ?: run { return }
        DumbService.getInstance(project).runWhenSmart {
            ModuleAnalysisService(project).performAnalysis()
//            val tokenizer = MBartSentencePieceTokenizer(
//                "sentencepiece.bpe.model",
//                mapOf(
//                    "<s>" to 0,
//                    "<pad>" to 1,
//                    "</s>" to 2,
//                    "<lang=ru_RU>" to 250001,
//                    "<lang=en_XX>" to 250004
//                )
//            )
//
//            val translator = MBartTranslator(
//                "mbart/encoder_model.onnx",
//                "mbart/decoder_model.onnx",
//                "mbart/decoder_with_past_model.onnx",
//                tokenizer
//            )
//
//            val result = translator.translate(
//                "Привет мир",
//                "ru_RU",
//                "en_XX"
//            )
//
//            println(result)
        }
    }

}


