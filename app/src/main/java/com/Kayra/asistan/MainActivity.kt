                drawLine(
                    color = Cyan.copy(
                        alpha = if (listening) 0.9f else 0.35f
                    ),
                    start = androidx.compose.ui.geometry.Offset(
                        x,
                        (size.height - barHeight) / 2f
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        x,
                        (size.height + barHeight) / 2f
                    ),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        InfoPanel(
            "INPUT / SESLİ KOMUT",
            recognizedText
        )

        Spacer(Modifier.height(12.dp))

        InfoPanel(
            "KAYRA / YANIT",
            responseText
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onMicClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Cyan,
                contentColor = Background
            )
        ) {
            Text(
                if (listening) "DİNLENİYOR..."
                else "◉  KAYRA'YI ETKİNLEŞTİR",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(25.dp))

        Text(
            "KAYRA ARTIFICIAL INTELLIGENCE",
            color = Color.Gray,
            fontSize = 9.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun StatusItem(title: String, value: String) {
    Column(
        modifier = Modifier
            .background(
                Panel,
                RoundedCornerShape(10.dp)
            )
            .padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = Color.Gray,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            value,
            color = Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InfoPanel(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Panel,
                RoundedCornerShape(14.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = value,
            color = WhiteBlue,
            fontSize = 14.sp
        )
    }
}
