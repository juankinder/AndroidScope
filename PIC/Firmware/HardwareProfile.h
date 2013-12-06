#define tris_self_power     TRISAbits.TRISA2    // Input
#define self_power          1

#define tris_usb_bus_sense  TRISAbits.TRISA1    // Input
#define USB_BUS_SENSE       1

#define PIC18F2550_USB
#define CLOCK_FREQ 48000000


/** LED ************************************************************/
#define mInitAllLEDs()      LATC &= 0xF0; TRISC &= 0xF0;

#define mLED_1              LATCbits.LATC0
#define mLED_2              LATCbits.LATC1
#define mLED_3              LATCbits.LATC2

#define mGetLED_1()         mLED_1
#define mGetLED_2()         mLED_2
#define mGetLED_3()         mLED_3

#define mLED_1_On()         mLED_1 = 1;
#define mLED_2_On()         mLED_2 = 1;
#define mLED_3_On()         mLED_3 = 1;

#define mLED_1_Off()        mLED_1 = 0;
#define mLED_2_Off()        mLED_2 = 0;
#define mLED_3_Off()        mLED_3 = 0;

#define mLED_1_Toggle()     mLED_1 = !mLED_1;
#define mLED_2_Toggle()     mLED_2 = !mLED_2;
#define mLED_3_Toggle()     mLED_3 = !mLED_3;


/** ADC Canal A **************************************************/
#define mInitADC_ChA()      TRISAbits.TRISA0 = INPUT_PIN; ADCON0 = 1; ADCON1 = 0b00001110; ADCON2 = 0b000101101;
//TRISAbits.TRISA0 = INPUT_PIN;     A0 como entrada
//ADCON0 = 1;                       ADC habilitado
//ADCON1 = 0b00011110;              VREF+ = AN3, Solo AN0 - (1011) para AN3 tambien como analogico
//ADCON2 = 0b00010111;              4 Tad, RC clock

/** Interrupciones ADC **********************************************************/
#define mInitADC_Int()      PIR1bits.ADIF = 0; //PIE1bits.ADIE = 1; RCONbits.IPEN = 1;
//PIR1bits.ADIF = 0;    ADC Interrupt flag cleared
//PIE1bits.ADIE = 1;    ADC Interruption enabled
//RCONbits.IPEN = 1;    Enable Interrupt priority levels


/** Timer0 **********************************************************/
#define mInitTimer0()   T0CONbits.T08BIT = 1; T0CONbits.T0CS = 0; T0CONbits.PSA = 0; T0CONbits.T0PS2 = 0; T0CONbits.T0PS1 = 0; T0CONbits.T0PS0 = 0;
//T0CONbits.T08BIT = 1;     TMR0 configurado como un contador de 8 bits
//T0CONbits.T0CS = 0;       Clock interno
//T0CONbits.PSA = 0;        Prescaler para TMR0 asignado
//T0CONbits.T0PS2 = 0;      Preescaler en 1:2
//T0CONbits.T0PS1 = 0;
//T0CONbits.T0PS0 = 0;
//TMR0H = 0x67;
//TMR0L = 0x69;

/** Dispara el Timer0 ***********************************************/
#define mStartTimer0()      T0CONbits.TMR0ON = 1;
//T0CONbits.TMR0ON = 1;     Habilita el Timer0

#define mStopTimer0()       T0CONbits.TMR0ON = 0;
//T0CONbits.TMR0ON = 0;     Deshabilita el Timer0

/** Interrupciones TIMER0 **********************************************************/
#define mInitTMR0_Int()     INTCONbits.TMR0IE = 1; INTCON2bits.TMR0IP = 1; RCONbits.IPEN = 1;
//INTCONbits.TMR0IE = 1;    TMR0 Interruption enabled
//INTCON2bits.TMR0IP = 1;   TMR0 high priority
//RCONbits.IPEN = 1;        Enable Interrupt priority levels


/** SPI **********************************************************/
//#define csSPI               LATBbits.LATB0

//#define mInitSPI()          TRISBbits.TRISB0 = OUTPUT_PIN; TRISBbits.TRISB1 = OUTPUT_PIN; TRISCbits.TRISC7 = OUTPUT_PIN;
//TRISBbits.TRISB0 = OUTPUT_PIN;    B0 como salida -> CS
//TRISBbits.TRISB1 = OUTPUT_PIN;    B1 como salida -> SCK
//TRISCbits.TRISC7 = OUTPUT_PIN;    C7 como salida -> SDO

//#define enableSPI()          csSPI = 0;
//#define disableSPI()         csSPI = 1;


/** I/O pin definitions ********************************************/
#define INPUT_PIN 1
#define OUTPUT_PIN 0