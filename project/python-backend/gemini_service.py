import google.generativeai as genai
import os

# 환경 변수에서 API 키를 가져옵니다. 환경 변수가 없을 경우를 대비해 기본값을 설정하거나 예외 처리를 할 수 있습니다.
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "AIzaSyBXWSMtiVj48WSbIfY30Ua3JEFY8sPFFdM")

def generate_gemini(text: str, stream: bool = False):
    """
    Gemini 모델을 사용하여 텍스트를 생성합니다.
    """
    genai.configure(api_key=GEMINI_API_KEY)
    model = genai.GenerativeModel('gemini-2.5-flash')
    
    return model.generate_content(text, stream=stream)