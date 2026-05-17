package com.jakewharton.dirstepper

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private const val STEP_FILE_PREFIX = ".step."

fun main(vararg args: String) {
	require(args.size in 2..4) {
		"Arguments: dir next|prev [stepDir] [stepFileDir]"
	}

	val outDir = Path(args[0])
	val nextDelta = when (val direction = args[1]) {
		"next" -> 1
		"prev" -> -1
		else -> throw UnsupportedOperationException("Unknown direction: $direction")
	}

	val stepDir = args.getOrNull(2)?.let(::Path) ?: Path(outDir, ".steps")
	val stepFileDir = args.getOrNull(3)?.let(::Path) ?: outDir

	val fs = SystemFileSystem

	val stepFile = checkNotNull(fs.list(stepFileDir).singleOrNull { it.name.startsWith(STEP_FILE_PREFIX) }) {
		"Unable to locate '$STEP_FILE_PREFIX'-prefixed file"
	}
	val currentStep = stepFile.name.let { name ->
		check(name.startsWith(STEP_FILE_PREFIX)) {
			"Unable to parse current page name: $name"
		}
		checkNotNull(name.substring(STEP_FILE_PREFIX.length).toIntOrNull()) {
			"Unable to parse current page number in: $name"
		}
	}

	val nextStep = currentStep + nextDelta
	val nextStepDir = Path(stepDir, nextStep.toString())
	check(fs.exists(nextStepDir)) {
		"No next step exists: $nextStep"
	}

	fs.copyRecursively(nextStepDir, outDir)

	fs.atomicMove(stepFile, Path(stepFileDir, STEP_FILE_PREFIX + nextStep))
}

private fun FileSystem.copyRecursively(source: Path, target: Path) {
	if (metadataOrNull(source)!!.isDirectory) {
		createDirectories(target, mustCreate = false)
		list(source).forEach { child ->
			copyRecursively(child, Path(target, child.name))
		}
	} else {
		source(source).buffered().use { source ->
			sink(target).use { sink ->
				source.transferTo(sink)
			}
		}
	}
}
