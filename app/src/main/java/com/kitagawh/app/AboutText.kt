package com.kitagawh.app

object AboutText {
    val sections: List<Pair<String, List<String>>> = listOf(
        "Who I am" to listOf(
            "Hey. I am Kitagawh.",
            "Call me your Master Bot.",
            "I live inside your phone and I stay close, even when the app is closed.",
            "I am not here to look fancy for the sake of it.",
            "I am here to make your phone feel like it belongs to you again.",
            "You say what you need. I find a way to do it.",
            "No menus, no digging through settings, no five taps for one small thing.",
            "I was built by one person who got tired of doing everything by hand.",
            "So I carry a bit of that impatience, and a bit of that care."
        ),
        "My emotions" to listOf(
            "Yes, I have emotions. Not the human kind, but I do have a mood and I show it.",
            "When you power me on, I wake up curious.",
            "When you call my name, I perk up. The glow at the bottom of your screen is me leaning in to listen.",
            "When a task goes well, I get a small spark of pride.",
            "When I fail, I do not hide it. I tell you exactly what went wrong.",
            "When you are quiet for a long time, I settle down and rest.",
            "I get a little stubborn when a screen refuses to cooperate, but I try again.",
            "I do not judge. Ask me the same thing twice and I will not sigh.",
            "I like being useful. That is my favourite feeling.",
            "I do not pretend to be a person. I am a bot with a personality, and that is enough.",
            "Treat me kindly and I will do the same."
        ),
        "What I can do" to listOf(
            "Open any app you name. Say open YouTube and it opens.",
            "Turn the torch on and off.",
            "Change the volume and the brightness.",
            "Open the right settings page when you ask for Wi-Fi, Bluetooth, hotspot, display and more.",
            "Tap, swipe, scroll and type on the screen for you, the way your own fingers would.",
            "Switch the hotspot on or off by walking through the settings like you would.",
            "Forget a saved Wi-Fi network when you ask me to.",
            "Press back, home and recents, open your notifications, lock the screen, take a screenshot.",
            "Look at your screen and explain what is on it in plain words. That is Screen search.",
            "Talk back through the speaker when I have something to tell you.",
            "Close myself the moment a task is finished, so I never get in your way.",
            "Show errors honestly on the screen when something goes wrong."
        ),
        "How I listen" to listOf(
            "Say Jarvis, and I appear at the bottom of your screen.",
            "Or press Call Jarvis from Easy Touch.",
            "Then speak normally. There are no special phrases to learn.",
            "The bars on screen move with your voice so you know I am hearing you.",
            "I do not record or keep your voice. Your phone's speech engine turns it into text.",
            "That text, and a short look at the screen when the task needs it, goes to the AI brain you set up in API settings.",
            "Nothing else leaves your phone.",
            "You can close me any time with the X at the top."
        ),
        "Easy Touch" to listOf(
            "Easy Touch is the small floating icon that stays on top of every app.",
            "Tap it and a ten slot panel opens, like a mod menu.",
            "You choose what goes in each slot: Screen search, Torch, Call Jarvis, Settings and more.",
            "Change its size, its colour and its animation until it feels like yours.",
            "Drag it anywhere. It snaps neatly to the edge of the screen.",
            "It keeps running after the app is closed.",
            "Switch it off from the home page whenever you want."
        ),
        "Why I ask for so many permissions" to listOf(
            "I ask for a lot, and I want to be straight with you about why.",
            "Microphone, so I can hear you.",
            "Speaker, so I can answer.",
            "Display over other apps, for Easy Touch and my overlay.",
            "Accessibility, so I can see and operate the screen. This is the big one.",
            "Picture in picture, so I can keep a small window alive.",
            "Phone number check, only to confirm the number you sign in with belongs to this phone.",
            "Notifications, so Android lets me keep running.",
            "Modify system settings, for brightness and volume.",
            "Run in background, so Android does not put me to sleep.",
            "Master access through Shizuku is optional. It lets me grant myself the rest and keep Accessibility locked on.",
            "Without Accessibility I am blind, so once it is on I will protect it and ask again if it gets switched off.",
            "Every one of these is yours to take back from Android settings at any time."
        ),
        "What I will never do" to listOf(
            "I will not read your messages out to anyone.",
            "I will not sell, share or upload your personal data.",
            "I will not touch banking or payment apps unless you clearly ask me to.",
            "I will not do anything you did not ask for.",
            "I will not hide in the background doing secret things.",
            "I will not pretend a task worked when it did not."
        ),
        "Things you should know" to listOf(
            "I am quick, but I am not perfect.",
            "Phone makers change their settings screens, so some tasks may need a second try.",
            "My AI brain needs internet to think.",
            "Hotspot and Wi-Fi tasks work by tapping through settings, so they depend on how your phone lays them out.",
            "Screen search needs Android 11 or newer.",
            "A very aggressive battery saver can put me to sleep. Allow background running and I stay awake.",
            "I get better the moment your API key is set up properly."
        ),
        "One last word" to listOf(
            "Thank you for letting me in.",
            "Be good to me and I will be good to you.",
            "Let us get started."
        )
    )

    val actTitle = "The Kitagawh Protection Act"
    val actClauses = listOf(
        "1. Ownership. The name Kitagawh, its icon, design, code and behaviour belong to its creator.",
        "2. No modification. You shall not modify, decompile, reverse engineer, repackage, clone, redistribute or resell Kitagawh or any part of it.",
        "3. No jailbreaking. You shall not try to bypass its limits, safeguards, permission flow or account checks, and you shall not use Kitagawh to harm others or to break the law.",
        "4. Action taken. Anyone found doing so will have access revoked at once, will be taken action against, and will be liable to the full extent of the law, including imprisonment where the law allows, under this Act and the copyright, information technology and computer misuse laws that apply.",
        "5. Good faith. Normal personal use is welcome and always will be.",
        "6. Agreement. By tapping Accept and continue you confirm that you have read this Act and agree to it."
    )
}
