package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger

/** Semantic eligibility, absolute confidence and separation are all required. */
internal fun <T> uniqueBestCandidate(
    candidates: List<T>,
    minimumScore: Int,
    minimumGap: Int,
    score: (T) -> Int,
): T? {
    require(minimumGap > 0)
    if (candidates.isEmpty()) return null
    val ranked = candidates.sortedByDescending(score)
    val best = ranked.first()
    if (score(best) < minimumScore) return null
    val second = ranked.getOrNull(1) ?: return best
    return best.takeIf { score(best).toLong() - score(second).toLong() >= minimumGap }
}

internal fun <T> selectUniqueScoredCandidate(
    tag: String,
    candidates: List<T>,
    minimumScore: Int,
    minimumGap: Int,
    logger: ScanLogger?,
    score: (T) -> Int,
    describe: (T) -> String,
): T? {
    val selected = uniqueBestCandidate(candidates, minimumScore, minimumGap, score)
    if (selected != null) return selected
    val detail = if (candidates.isEmpty()) {
        "no semantic candidate"
    } else {
        "insufficient confidence (minimumScore=$minimumScore requiredGap=$minimumGap): " + candidates.sortedByDescending(score)
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
