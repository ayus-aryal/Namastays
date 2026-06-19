package com.example.namastays.trek.util

fun String.toCloudinaryThumbnail(width: Int = 600): String {
    return if (contains("res.cloudinary.com")) {
        replace("/upload/", "/upload/w_$width,c_fill,q_auto,f_auto/")
    } else {
        this // non-Cloudinary URL — pass through unchanged
    }
}