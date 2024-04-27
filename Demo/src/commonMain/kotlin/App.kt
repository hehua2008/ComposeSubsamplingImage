import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun App() {
    val mainViewModel = remember { MainViewModel() }

    MaterialTheme {
        val photoInfoList = remember {
            persistentListOf(
                UrlPhotoInfo(
                    original = "https://img.zcool.cn/community/01bgpi1i0ljjixjqx6i3dd3030.jpg",
                    thumb = "https://img.zcool.cn/community/01bgpi1i0ljjixjqx6i3dd3030.jpg?x-oss-process=image/auto-orient,1/resize,m_lfit,w_1280,limit_1/sharpen,100/quality,q_100",
                    width = 8736,
                    height = 11648
                ),
                UrlPhotoInfo(
                    original = "https://img.zcool.cn/community/01iebnipjxnrxyzogmeukm3137.jpg",
                    thumb = "https://img.zcool.cn/community/01iebnipjxnrxyzogmeukm3137.jpg?x-oss-process=image/auto-orient,1/resize,m_lfit,w_1280,limit_1/sharpen,100/quality,q_100",
                    width = 8488,
                    height = 11317
                ),
                UrlPhotoInfo(
                    original = "https://img.zcool.cn/community/010acub4lsklq3mdssijqj3033.jpg",
                    thumb = "https://img.zcool.cn/community/010acub4lsklq3mdssijqj3033.jpg?x-oss-process=image/auto-orient,1/resize,m_lfit,w_1280,limit_1/sharpen,100/quality,q_100",
                    width = 11648,
                    height = 8736
                ),
                UrlPhotoInfo(
                    original = "https://img.zcool.cn/community/01sje0cmj4wosofhfknrbo3732.jpg",
                    thumb = "https://img.zcool.cn/community/01sje0cmj4wosofhfknrbo3732.jpg?x-oss-process=image/auto-orient,1/resize,m_lfit,w_1280,limit_1/sharpen,100/quality,q_100",
                    width = 8273,
                    height = 11031
                ),
                UrlPhotoInfo(
                    original = "https://img.zcool.cn/community/01crgpytun6sf7aepiecdj3338.jpg",
                    thumb = "https://img.zcool.cn/community/01crgpytun6sf7aepiecdj3338.jpg?x-oss-process=image/auto-orient,1/resize,m_lfit,w_1280,limit_1/sharpen,100/quality,q_100",
                    width = 8736,
                    height = 11648
                ),
                UrlPhotoInfo(
                    original = "https://img.zcool.cn/community/01nujol3pbdeahzvq4siy63036.jpg",
                    thumb = "https://img.zcool.cn/community/01nujol3pbdeahzvq4siy63036.jpg?x-oss-process=image/auto-orient,1/resize,m_lfit,w_1280,limit_1/sharpen,100/quality,q_100",
                    width = 8736,
                    height = 11648
                ),
                UrlPhotoInfo(
                    original = "https://img.zcool.cn/community/01xo46wgdapt5yokpccdrc3432.jpg",
                    thumb = "https://img.zcool.cn/community/01xo46wgdapt5yokpccdrc3432.jpg?x-oss-process=image/auto-orient,1/resize,m_lfit,w_1280,limit_1/sharpen,100/quality,q_100",
                    width = 11648,
                    height = 8736
                )
            )
        }

        ImageHorizontalPager(
            mainViewModel = mainViewModel,
            photoInfoList = photoInfoList,
            initialIndex = 1,
            modifier = Modifier.fillMaxSize()
        )
    }
}
