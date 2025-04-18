package app.composeShot.anter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.composeShot.anter.ui.theme.ComposeShotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeShotTheme {
                val context = LocalContext.current
                Box(){
                    WeatherHeader(
                        city = "New York",
                        date = "Thursday, April 18",
                        temperature = "23°C",
                        weatherDescription = "Partly Cloudy",
                        weatherIconRes = R.drawable.ic_cloudy,
                        backgroundImageRes = R.drawable.bg_sky
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        ShareRoundButton(
                            onClick = {
                                // Handle share action
                                ComposableShotsUtil.captureAndShare(
                                    context = context,
                                    content = {
                                        WeatherHeader(
                                            city = "New York",
                                            date = "Thursday, April 18",
                                            temperature = "23°C",
                                            weatherDescription = "Partly Cloudy",
                                            weatherIconRes = R.drawable.ic_cloudy,
                                            backgroundImageRes = R.drawable.bg_sky
                                        )
                                    },
                                    title = "Check out my app!"
                                )
                            }
                        )
                    }
                }

            }
        }
    }


    @Composable
    fun WeatherHeader(
        city: String,
        date: String,
        temperature: String,
        weatherDescription: String,
        weatherIconRes: Int, // Use painterResource(R.drawable.your_icon)
        backgroundImageRes: Int // Background image resource ID
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            // Background image
            Image(
                painter = painterResource(id = backgroundImageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            // Scrim overlay (for better text visibility)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Header content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = city,
                    style = MaterialTheme.typography.headlineMedium.copy(color = Color.White),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = weatherIconRes),
                        contentDescription = weatherDescription,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = temperature,
                            style = MaterialTheme.typography.headlineSmall.copy(color = Color.White)
                        )
                        Text(
                            text = weatherDescription.capitalize(),
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.9f))
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ShareRoundButton(
        onClick: () -> Unit
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp) // Round button size
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.White,
                tonalElevation = 4.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(8.dp) // Inner padding for the icon
                )
            }
        }
    }
}