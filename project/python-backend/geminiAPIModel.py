import google.generativeai as genai


# ⚠️ 보안 경고: API 키가 코드에 노출되었습니다. 테스트 후 반드시 키를 재발급받고 환경 변수로 관리하세요.
GEMINI_API_KEY = "AIzaSyBXWSMtiVj48WSbIfY30Ua3JEFY8sPFFdM"

genai.configure(api_key=GEMINI_API_KEY)

model = genai.GenerativeModel('gemini-2.5-flash')

response = model.generate_content("너의 이름은 뭐니?")

print(response.text)


# if __name__ == "__main__":
#     module_name = Path(__file__).stem
#     uvicorn.run(f"{module_name}:app", host="0.0.0.0", port=8000, reload=True)
