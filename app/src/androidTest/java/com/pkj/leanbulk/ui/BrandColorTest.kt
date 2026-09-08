package com.pkj.leanbulk.ui

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.ui.theme.BrandGreen
import com.pkj.leanbulk.ui.theme.BrandInk
import com.pkj.leanbulk.ui.theme.BrandLime
import com.pkj.leanbulk.ui.theme.DarkShaColors
import com.pkj.leanbulk.ui.theme.LightShaColors
import com.pkj.leanbulk.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 브랜드색을 바꿔도 **읽히는 것이 유지되는지**를 못 박는다.
 *
 * 초록 계열은 전부 [BrandLime]에서 파생되므로(`Color.kt`), 라임 하나만 바꿔도 화면 전체가
 * 따라온다. 그런데 라임을 어느 쪽으로 옮기느냐에 따라 파생값이 기준을 못 넘길 수 있어서,
 * 색을 고른 뒤 눈으로만 확인하면 밝은 데서 안 읽히는 걸 놓친다.
 *
 * 기준: 작은 글자 4.5:1, 채운 버튼 위 글자도 글자라 같은 기준을 쓴다.
 */
@RunWith(AndroidJUnit4::class)
class BrandColorTest {

    private fun assertReadable(name: String, fg: Color, bg: Color, min: Float = 4.5f) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$name 대비가 ${"%.1f".format(ratio)}:1로 $min:1에 못 미친다", ratio >= min)
    }

    @Test
    fun 밝은_테마에서_읽힌다() = with(LightShaColors) {
        assertReadable("채운 버튼 글자", onPrimaryFill, primaryFill)
        assertReadable("링크 글자", primary, surfaceRaised)
        assertReadable("링크 글자(화면 바탕)", primary, surface)
        assertReadable("연한 바탕 위 글자", primaryWeakFg, primaryWeakBg)
        assertReadable("본문", bodyText, surfaceRaised)
        assertReadable("보조 글자", muted, surfaceRaised)
        assertReadable("위험", danger, surfaceRaised)
    }

    @Test
    fun 어두운_테마에서_읽힌다() = with(DarkShaColors) {
        assertReadable("채운 버튼 글자", onPrimaryFill, primaryFill)
        assertReadable("링크 글자", primary, surfaceRaised)
        assertReadable("링크 글자(화면 바탕)", primary, surface)
        assertReadable("연한 바탕 위 글자", primaryWeakFg, primaryWeakBg)
        assertReadable("본문", bodyText, surfaceRaised)
        assertReadable("보조 글자", muted, surfaceRaised)
        assertReadable("위험", danger, surfaceRaised)
    }

    /**
     * 라임을 글자 색으로 쓰면 안 된다는 규칙의 근거. 이 단언이 깨진다면 라임이 충분히
     * 어두워졌다는 뜻이고, 그러면 "채우고 잉크 글자"라는 전제부터 다시 봐야 한다.
     */
    @Test
    fun 라임은_흰_바탕_글자로_못_쓴다() {
        assertTrue(
            "라임이 흰 바탕에서 읽힐 만큼 어두워졌다. Color.kt의 전제를 다시 볼 것",
            contrastRatio(BrandLime, Color.White) < 4.5f
        )
    }

    /** 파생된 딥그린은 라임과 **다른 색이어야** 한다 — 안 내려갔으면 파생이 안 된 것이다. */
    @Test
    fun 글자용_초록은_라임에서_내려온_다른_값이다() {
        assertTrue("파생이 안 됐다", BrandGreen != BrandLime)
        assertTrue("잉크보다 어두워졌다", BrandGreen.luminanceOf() > BrandInk.luminanceOf())
    }

    private fun Color.luminanceOf(): Float = contrastRatio(this, Color.Black)
}
