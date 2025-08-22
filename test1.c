#include <stdio.h>

int main(void)
{
	int age, score;  // 정수 변수 선언문 
	
	printf("나이가 어떻게 되세요? : ");	scanf("%d", &age);
	printf("점수는요? : ");	scanf("%d", &score);
	
	printf("\n\nMy age : %d \n", age);
	printf("%d is my point \n", score);
	printf("Good \nmorning \neverybody\n");

	return 0;
}
