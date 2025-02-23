package com.example.cpplearner.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.abs

class CustomDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : DrawerLayout(context, attrs, defStyle) {

    private var startX: Float = 0f
    private var startY: Float = 0f
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var isSwipeGesture = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isSwipeGesture = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = ev.x - startX
                val deltaY = ev.y - startY

                // Check if horizontal swipe
                if (abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY) * 1.5f) {
                    // For right swipe (deltaX > 0)
                    if (deltaX > 0 && !isDrawerOpen(GravityCompat.START)) {
                        isSwipeGesture = true
                        return true
                    }
                }
            }
        }
        return if (isSwipeGesture) {
            true
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isSwipeGesture) {
            when (ev.action) {
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = ev.x - startX
                    if (deltaX > touchSlop && !isDrawerOpen(GravityCompat.START)) {
                        openDrawer(GravityCompat.START)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSwipeGesture = false
                }
            }
        }
        return super.onTouchEvent(ev)
    }
}