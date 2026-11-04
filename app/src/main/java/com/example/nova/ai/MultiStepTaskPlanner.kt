package com.example.nova.ai

import java.util.Locale

/**
 * Multi-step Task Planner for compound voice commands in Nova AI.
 * Example: "Open WhatsApp, text Rahul, and set alarm for 7 AM"
 */
object MultiStepTaskPlanner {

    data class SubTask(
        val stepNumber: Int,
        val intent: IntentDetector.ClassifiedIntent,
        val rawCommand: String
    )

    data class TaskPlan(
        val fullCommand: String,
        val tasks: List<SubTask>
    )

    fun planTasks(compoundCommand: String): TaskPlan {
        val delimiters = listOf(" and then ", " then ", " and ", " pachhi ", " પછી ", " અને ")
        var rawSteps = listOf(compoundCommand)

        for (delimiter in delimiters) {
            val nextList = mutableListOf<String>()
            for (step in rawSteps) {
                if (step.contains(delimiter, ignoreCase = true)) {
                    nextList.addAll(step.split(delimiter, ignoreCase = true))
                } else {
                    nextList.add(step)
                }
            }
            rawSteps = nextList
        }

        val subTasks = rawSteps.mapIndexed { index, stepStr ->
            val cleanStep = stepStr.trim()
            SubTask(
                stepNumber = index + 1,
                intent = IntentDetector.detectIntent(cleanStep),
                rawCommand = cleanStep
            )
        }

        return TaskPlan(fullCommand = compoundCommand, tasks = subTasks)
    }
}
