package com.example.picoflexxtest.royale

class RoyaleCameraException(message: String, val code: Int, val codeMessage: String) :
    Exception("$message (Code $code: $codeMessage)")
