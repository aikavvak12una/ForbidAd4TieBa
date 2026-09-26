package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger

/** Candidates must already satisfy the semantic anchors and absolute score threshold. */
internal fun <T> uniqueBestCandidate(
    candidates: List<T>,
    minimumGap: Int,
    score: (T) -> Int,
): T? {
    require(minimumGap > 0)
    if (candidates.isEmpty()) return null
    val ranked = candidates.sortedByDescending(score)
    val best = ranked.first()
    val second = ranked.getOrNull(1) ?: return best
    return best.takeIf { score(best).toLong() - score(second).toLong() >= minimumGap }
}

internal fun <T> selectUniqueScoredCandidate(
    tag: String,
    candidates: List<T>,
    minimumGap: Int,
    logger: ScanLogger?,
    score: (T) -> Int,
    describe: (T) -> String,
): T? {
    val selected = uniqueBestCandidate(candidates, minimumGap, score)
    if (selected != null) return selected
    val detail = if (candidates.isEmpty()) {
        "no semantic candidate"
    } else {
        "ambiguous (requiredGap=$minimumGap): " + candidates.sortedByDescending(score)
            .joinToString(",") { "${describe(it)}:${score(it)}" }
    }
    val errors = HookSymbolScanSession.get()?.scanErrors
    if (candidates.isNotEmpty() && errors != null) {
        HookSymbolScanDiagnostics.recordScanIssue(logger, tag, errors, detail)
    } else {
        HookSymbolScanDiagnostics.log(logger, "$tag: $detail")
    }
    return null
}
