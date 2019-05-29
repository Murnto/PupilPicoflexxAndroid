package com.example.picoflexxtest.royale

class RoyaleCameraException(message: String, code: Int, codeMessage: String) :
    Exception("$message (Code $code: $codeMessage)")
