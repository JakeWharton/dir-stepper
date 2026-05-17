package com.jakewharton.dirstepper

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private const val STEP_FILE_PREFIX = ".step."

fun main(vararg args: String) {
	require(args.size == 4) {
		"Wrong number of arguments: ${args.size}"
	}
	val (stepFileDirPath, stepDirPath, outDirPath, direction) = args

	val stepFileDir = Path(stepFileDirPath)
	val stepDir = Path(stepDirPath)
	val outDir = Path(outDirPath)
	val nextDelta = when (direction) {
		"next" -> 1
		"prev" -> -1
		else -> throw UnsupportedOperationException("Unknown direction: $direction")
	}

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
