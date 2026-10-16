package com.heysig.watch

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Bundle validation candidate only; production companion pairing is the next milestone. */
class WatchActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48,64,48,64)
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(TextView(this).apply { text = "HeySig Watch"; textSize = 22f; setTextColor(Color.WHITE) })
        layout.addView(TextView(this).apply {
            text = "Pairing is not available in this test build. Hearing-aid controls are unavailable."
            textSize = 16f; setTextColor(Color.WHITE)
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(layout) })
    }
}
