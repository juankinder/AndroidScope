/** Includes *******************************************************/
#include "./USB/usb.h"
#include "./USB/usb_function_cdc.h"
#include "./USB/usb_device.h"
#include "usb_config.h"
#include "GenericTypeDefs.h"
#include "Compiler.h"
#include "spi.h"

#include "HardwareProfile.h"

/** Configuracion **************************************************/
/* Configuracion del dispisitivo */
#pragma config FOSC = HSPLL_HS          // 20 MHz crystal.
#pragma config PLLDIV = 5               // Divide by 5 to provide the 96 MHz PLL with 4 MHz input.
#pragma config CPUDIV = OSC1_PLL2       // Divide 96 MHz PLL output by 2 to get 48 MHz system clock.
#pragma config USBDIV = 2               // "USB clock source comes from the 96 MHz PLL divided by 2."
#pragma config FCMEN = OFF              // "Fail-Safe Clock Monitor disabled."
#pragma config IESO = OFF               // "Oscillator Switchover mode disabled."
#pragma config PWRT = OFF               // "PWRT disabled."
#pragma config WDT = OFF                // "HW Disabled - SW Controlled."
#pragma config MCLRE = ON               // "MCLR pin enabled; RE3 input pin disabled."
#pragma config BOR = ON                 // Brown Out Reset enabled in hardware. In case of supply voltage drop, BOR resets the device to prevent erratic CPU behavior.
#pragma config BORV = 3                 // BOR occurs at 2V (1.2V below the minimum required voltage for the HSPLL oscillator mode).
#pragma config VREGEN = ON              // USB Voltage Regulator
#pragma config LVP = OFF                // Disable low voltage ICSP
#pragma config CP0 = OFF                // Disable code protection


/** Variables ********************************************************/
#pragma udata SMALLDATA
int buffer_pointer;
char TMR_LOW;
char GAIN;


#pragma udata BIGDATA
char buffer_out[CDC_DATA_OUT_EP_SIZE];

/** Prototipos privados ***************************************/
static void InitializeSystem(void);
void ProcessIO(void);
void USBDeviceTasks(void);
void YourHighPriorityISRCode();
void YourLowPriorityISRCode();
void addResult(char);
void SendData(char *);
void USBCBSendResume(void);
void BlinkUSBStatus(void);
//void setZoom(char);
void setBufferSize(char);
void processUsbCommands(void);
void SPI_Init();
void setAmplifierGain(unsigned char);



/** VECTOR REMAPPING ***********************************************/
#define PROGRAMMABLE_WITH_USB_HID_BOOTLOADER

#if defined(PROGRAMMABLE_WITH_USB_HID_BOOTLOADER)
        #define REMAPPED_RESET_VECTOR_ADDRESS		0x1000
        #define REMAPPED_HIGH_INTERRUPT_VECTOR_ADDRESS	0x1008
        #define REMAPPED_LOW_INTERRUPT_VECTOR_ADDRESS	0x1018
#else
        #define REMAPPED_RESET_VECTOR_ADDRESS		0x00
        #define REMAPPED_HIGH_INTERRUPT_VECTOR_ADDRESS	0x08
        #define REMAPPED_LOW_INTERRUPT_VECTOR_ADDRESS	0x18
#endif

#if defined(PROGRAMMABLE_WITH_USB_HID_BOOTLOADER)
extern void _startup (void);        // See c018i.c in your C18 compiler dir
#pragma code REMAPPED_RESET_VECTOR = REMAPPED_RESET_VECTOR_ADDRESS
void _reset (void)
{
    _asm goto _startup _endasm
}
#endif
#pragma code REMAPPED_HIGH_INTERRUPT_VECTOR = REMAPPED_HIGH_INTERRUPT_VECTOR_ADDRESS
void Remapped_High_ISR (void)
{
     _asm goto YourHighPriorityISRCode _endasm
}
#pragma code REMAPPED_LOW_INTERRUPT_VECTOR = REMAPPED_LOW_INTERRUPT_VECTOR_ADDRESS
void Remapped_Low_ISR (void)
{
     _asm goto YourLowPriorityISRCode _endasm
}

#if defined(PROGRAMMABLE_WITH_USB_HID_BOOTLOADER)
#pragma code HIGH_INTERRUPT_VECTOR = 0x08
void High_ISR (void)
{
     _asm goto REMAPPED_HIGH_INTERRUPT_VECTOR_ADDRESS _endasm
}
#pragma code LOW_INTERRUPT_VECTOR = 0x18
void Low_ISR (void)
{
     _asm goto REMAPPED_LOW_INTERRUPT_VECTOR_ADDRESS _endasm
}
#endif

#pragma code


#pragma interrupt YourHighPriorityISRCode
void YourHighPriorityISRCode()
{
    //Interrupcion del TIMER 0
    if (INTCONbits.TMR0IF){
        addResult(ADRESH);          //Lee la muestra previa
        INTCONbits.TMR0IF = 0;
        TMR0L = TMR_LOW;

        ADCON0bits.GO_DONE = 1;     //Dispara el ADC
    }

    USBDeviceTasks();
}
#pragma interruptlow YourLowPriorityISRCode
void YourLowPriorityISRCode()
{
}


/** DECLARACIONES ***************************************************/
#pragma code

void addResult(char data2add)
{
    buffer_out[buffer_pointer] = data2add;
    buffer_pointer++;
}

void main(void)
{
    InitializeSystem();

    buffer_pointer = 0;

    // Valores del Timer por defecto
    TMR_LOW = 0X90;     //E0 toma mejor las muestras //FB valor optimo

    while(1)
    {
        if(USB_BUS_SENSE && (USBGetDeviceState() == DETACHED_STATE))
        {
            USBDeviceAttach();
        }
        ProcessIO();

        if (buffer_pointer >= CDC_DATA_OUT_EP_SIZE)
        {
            buffer_pointer = 0;
            mLED_3_Toggle();
            SendData(buffer_out);
        }
    }
}

static void InitializeSystem(void)
{
    ADCON1 |= 0x0F;     //Todos los pines como entradas por defecto

    //Inicializacion de los Leds
    mInitAllLEDs();

    //Inicializacion del modulo USB
    USBDeviceInit();

    //Inicializacion del ADC
    mInitADC_ChA();

    //Inicializacion del Timer 0
    mInitTimer0();

    //Inicializacion de las interrupciones
    mInitTMR0_Int();
    mInitADC_Int();

    //Inicializacion del SPI
    SPI_Init();
    setAmplifierGain(0xE1);
}

void ProcessIO(void)
{
    //Titila los leds de acuerdo al estado del USB
    BlinkUSBStatus();

    if((USBDeviceState < CONFIGURED_STATE)||(USBSuspendControl==1)) return;

    processUsbCommands();

    CDCTxService();
}

void BlinkUSBStatus(void)
{
    static WORD led_count=0;

    if(led_count == 0)led_count = 10000U;
    led_count--;

    #define mLED_Both_Off()         {mLED_1_Off();mLED_2_Off();}
    #define mLED_Both_On()          {mLED_1_On();mLED_2_On();}
    #define mLED_Only_1_On()        {mLED_1_On();mLED_2_Off();}
    #define mLED_Only_2_On()        {mLED_1_Off();mLED_2_On();}

    if(USBSuspendControl == 1)
    {
        if(led_count==0)
        {
            mLED_1_Toggle();
            if(mGetLED_1())
            {
                mLED_2_On();
            }
            else
            {
                mLED_2_Off();
            }
        }
    }
    else
    {
        if(USBDeviceState == DETACHED_STATE)
        {
            mLED_Both_Off();
        }
        else if(USBDeviceState == ATTACHED_STATE)
        {
            mLED_Both_On();
        }
        else if(USBDeviceState == POWERED_STATE)
        {
            mLED_Only_1_On();
        }
        else if(USBDeviceState == DEFAULT_STATE)
        {
            mLED_Only_2_On();
        }
        else if(USBDeviceState == ADDRESS_STATE)
        {
            if(led_count == 0)
            {
                mLED_1_Toggle();
                mLED_2_Off();
            }
        }
        else if(USBDeviceState == CONFIGURED_STATE)
        {
            if(led_count==0)
            {
                mLED_1_Toggle();
                if(mGetLED_1())
                {
                    mLED_2_Off();
                }
                else
                {
                    mLED_2_On();
                }
            }
        }
    }

}

// Envia datos por USB
void SendData(char *data)
{
    if((USBDeviceState < CONFIGURED_STATE)||(USBSuspendControl==1)) return;

    if(mUSBUSARTIsTxTrfReady())
    {
        putUSBUSART(data, CDC_DATA_OUT_EP_SIZE);
    }
}

// Procesa los comandos recibidos por USB
void processUsbCommands(void)
{

    char numBytesRead;
    char USB_Out_Buffer[3];

    if(USBUSARTIsTxTrfReady())
    {
        numBytesRead = getsUSBUSART(USB_Out_Buffer, 3);
        if(numBytesRead != 0)
        {
            switch(USB_Out_Buffer[0]){
                case 0xA0:
                    setBufferSize(USB_Out_Buffer[1]);
                    break;
                case 0xC0:
                    // Timer
                    TMR_LOW = USB_Out_Buffer[1];
                    break;
                case 0xD0:
                    // Pausa
                    mStopTimer0();
                    //setAmplifierGain(0xE6);
                    break;
                case 0xD1:
                    // Play
                    mStartTimer0();
                    //setAmplifierGain(0xE6);
                    break;
                case 0xD2:
                    // Zoom Vertical
                    setAmplifierGain(USB_Out_Buffer[1]);
                    break;
            }
        }
    }
}


void SPI_Init()
{
    SSPCON1bits.SSPEN = 0;

    TRISBbits.TRISB0 = 0;   // B0 como salida -> CS (PIN 21)
    TRISBbits.TRISB1 = 0;   // B1 SCK (PIN 22)
    TRISCbits.TRISC7 = 0;   // Set SDO pin to output (Master mode) (PIN 18)

    SSPCON1bits.CKP = 1;  //Clock polarity =
    SSPSTATbits.CKE = 1;  //Transmit occurs on transition from idle to active clock state
    SSPSTATbits.SMP = 0; //Input data sampled at middle of data output time
    SSPCON1bits.SSPM3 = 0;  //
    SSPCON1bits.SSPM2 = 0;  //Set SPI clock to FOSC/64  this also sets the mode to SPI
    SSPCON1bits.SSPM1 = 1;  //
    SSPCON1bits.SSPM0 = 0;  //

    SSPCON1bits.SSPEN = 1;  //Go
}

void setAmplifierGain(unsigned char value)
{
    //Amplifier gain can only take the values
    //1,2,4,5,8,10,16 and 32  Parameter values other than these set the gain to
    //1.  I didn't bother to write code to handle funny values cos I thought I'd
    //be the only one using it.
    unsigned char gainfield;
    unsigned char dummy;
    
    switch(value)
    {
        case 0xE1:
            gainfield = 0b000;
            break;
        case 0xE2:
            gainfield = 0b001;
            break;
        case 0xE3:
            gainfield = 0b010;
            break;
        case 0xE4:
            gainfield = 0b011;
            break;
        case 0xE5:
            gainfield = 0b100;
            break;
        case 0xE6:
            gainfield = 0b101;
            break;
        case 0xE7:
            gainfield = 0b110;
            break;
        case 0xE8:
            gainfield = 0b111;
            break;
        default:
            gainfield = 0b101;
    }
    
    LATBbits.LATB0 = 0;     //Pull CS Low
    SSPBUF = 0b01000000;    //Choose the gain register
    while(!SSPSTATbits.BF)
        Nop();
    dummy = SSPBUF;
    SSPBUF = gainfield;
    while(!SSPSTATbits.BF)
        Nop();
    dummy = SSPBUF;    //read the data and throw it away
    Nop();
    Nop();
    Nop();
    Nop();
    LATBbits.LATB0 = 1;     //Pul CS High to execute the command
}


void setBufferSize(char value) {
    if (value > CDC_DATA_OUT_EP_SIZE) {
        value = CDC_DATA_OUT_EP_SIZE;
    }
}

// ******************************************************************************************************
// ************** USB Callback Functions ****************************************************************
// ******************************************************************************************************
void USBCBSuspend(void) {}

void USBCBWakeFromSuspend(void) {}

void USBCB_SOF_Handler(void) {}

void USBCBErrorHandler(void) {}

void USBCBCheckOtherReq(void)
{
    USBCheckCDCRequest();
}

void USBCBStdSetDscHandler(void) {}

void USBCBInitEP(void)
{
    //Enable the CDC data endpoints
    CDCInitEP();
}

void USBCBSendResume(void)
{
    static WORD delay_count;

    if(USBGetRemoteWakeupStatus() == TRUE)
    {
        if(USBIsBusSuspended() == TRUE)
        {
            USBMaskInterrupts();

            USBCBWakeFromSuspend();
            USBSuspendControl = 0;
            USBBusIsSuspended = FALSE;

            delay_count = 3600U;
            do
            {
                delay_count--;
            }while(delay_count);

            USBResumeControl = 1;
            delay_count = 1800U;
            do
            {
                delay_count--;
            }while(delay_count);
            USBResumeControl = 0; 
            USBUnmaskInterrupts();
        }
    }
}

BOOL USER_USB_CALLBACK_EVENT_HANDLER(int event, void *pdata, WORD size)
{
    switch( event )
    {
        case EVENT_TRANSFER:
            break;
        case EVENT_SOF:
            USBCB_SOF_Handler();
            break;
        case EVENT_SUSPEND:
            USBCBSuspend();
            break;
        case EVENT_RESUME:
            USBCBWakeFromSuspend();
            break;
        case EVENT_CONFIGURED:
            USBCBInitEP();
            break;
        case EVENT_SET_DESCRIPTOR:
            USBCBStdSetDscHandler();
            break;
        case EVENT_EP0_REQUEST:
            USBCBCheckOtherReq();
            break;
        case EVENT_BUS_ERROR:
            USBCBErrorHandler();
            break;
        case EVENT_TRANSFER_TERMINATED:
            break;
        default:
            break;
    }
    return TRUE;
}