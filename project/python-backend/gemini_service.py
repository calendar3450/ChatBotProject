import google.generativeai as genai

# ⚠️ 보안 경고: API 키가 코드에 노출되었습니다. 테스트 후 반드시 키를 재발급받고 환경 변수로 관리하세요.
GEMINI_API_KEY = "AIzaSyBXWSMtiVj48WSbIfY30Ua3JEFY8sPFFdM"

def generate_gemini(text: str, stream: bool = False):
    """
    Gemini 모델을 사용하여 텍스트를 생성합니다.
    """
    genai.configure(api_key=GEMINI_API_KEY)
    model = genai.GenerativeModel('gemini-2.5-flash')
    
    return model.generate_content(text, stream=stream)