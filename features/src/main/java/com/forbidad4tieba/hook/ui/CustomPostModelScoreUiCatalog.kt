package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.feature.ad.CustomPostModelScoreCatalog

internal data class ModelScoreUiItem(
    val key: String,
    val label: String,
    val description: String,
)

internal object CustomPostModelScoreUiCatalog {
    val items = listOf(
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.MSD_SCORE,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_MSD_SCORE_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_MSD_SCORE_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.MSD_DURATION_SCORE,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_MSD_DURATION_SCORE_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_MSD_DURATION_SCORE_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.DNN_PB_DUR_CTR_0,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_DNN_PB_DUR_CTR_0_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_DNN_PB_DUR_CTR_0_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.CUPAI_ALL_SCORES_1,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_1_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_1_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.CUPAI_ALL_SCORES_2,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_2_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_2_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.CUPAI_ALL_SCORES_3,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_3_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CUPAI_ALL_SCORES_3_DESC,
        ),
        ModelScoreUiItem(
            CustomPostModelScoreCatalog.CDNN_LTR,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CDNN_LTR_LABEL,
            UiText.Settings.CUSTOM_POST_FILTER_MODEL_CDNN_LTR_DESC,
        ),
    )
}
