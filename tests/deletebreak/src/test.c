#ifndef ANGELIX_OUTPUT
#define ANGELIX_OUTPUT(type, expr, id) expr
#define ANGELIX_REACHABLE(id)
#endif
#include<stdio.h>
int main(int argc, char *argv[])
{
	int i,a[5],b[101]={0},t=0,temp,max=0;
	for(i=0;i<5;i++)
	{
	scanf("%d",&a[i]);
b[a[i]]++;
t+=a[i];
}
	for(i=0;i<5;i++)
	{if(b[a[i]]>=3)
	{
	max=3*a[i];
	break;}	
}
	for(i=0;i<5;i++)
	{if(b[a[i]]==2)
	{temp=a[i]*2;
	if(temp>max)
	{
	max=temp;
	break;
	}
		}	
}
printf("%d",ANGELIX_OUTPUT(int, t-max, "angelixout"));
return 0;
}
