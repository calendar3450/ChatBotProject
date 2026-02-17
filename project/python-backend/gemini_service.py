import google.generativeai as genai
import os

try:
    import config
    GEMINI_API_KEY = config.GEMINI_API_KEY
except ImportError:
    # config.py가 없으면 환경 변수에서 가져오거나 빈 값 처리
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")

def generate_gemini(text: str, stream: bool = False):
    """
    Gemini 모델을 사용하여 텍스트를 생성합니다.
    """
    genai.configure(api_key=GEMINI_API_KEY)
    model = genai.GenerativeModel('gemini-2.5-flash')
    
    
    return model.generate_content(text, stream=stream)