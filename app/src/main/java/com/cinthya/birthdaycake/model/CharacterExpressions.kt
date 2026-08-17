package com.cinthya.birthdaycake.model

import com.cinthya.birthdaycake.R


enum class CharacterExpressions {
    IDLE_SIT,
    IDLE_STAND,
    LICKING_PAW,
    LAUGHING,
    SCRATCHING,
    SLEEPING,
    STAND_SIT,
    WAG_TAIL,
    YAWNING,
    ALERT
}

fun CharacterExpressions.toImageResource(): Int{
    return when(this){
        CharacterExpressions.IDLE_SIT -> R.drawable.cat_sit
        CharacterExpressions.IDLE_STAND -> R.drawable.cat_stand
        CharacterExpressions.LICKING_PAW -> R.drawable.cat_licking_paw
        CharacterExpressions.LAUGHING -> R.drawable.cat_laughing
        CharacterExpressions.SCRATCHING -> R.drawable.cat_scratching
        CharacterExpressions.SLEEPING -> R.drawable.cat_sleeping
        CharacterExpressions.STAND_SIT -> R.drawable.cat_stand_sit
        CharacterExpressions.WAG_TAIL -> R.drawable.cat_wag_tail
        CharacterExpressions.YAWNING -> R.drawable.cat_yawn
        CharacterExpressions.ALERT -> R.drawable.cat_alert
    }
}