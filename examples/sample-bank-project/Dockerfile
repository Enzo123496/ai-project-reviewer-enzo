FROM openjdk:latest
ENV DB_PASSWORD=admin123
COPY . /app
CMD ["java", "-cp", "/app/target/classes", "com.example.bank.BankService"]
