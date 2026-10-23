package com.zodiacguard.app.ui.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.navOptions
import com.zodiacguard.app.R

fun NavController.navigateWithTransition(destinationId: Int, args: Bundle? = null) {
    navigate(destinationId, args, navOptions {
        anim {
            enter = R.anim.screen_enter
            exit = R.anim.screen_exit
            popEnter = R.anim.screen_pop_enter
            popExit = R.anim.screen_pop_exit
        }
    })
}
