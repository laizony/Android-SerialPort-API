package android.serialport.sample.gps;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import android.annotation.SuppressLint;
import android.util.Log;

/**
 * 标准nmea数据解析
 * @author mmsx
 *
 * https://blog.csdn.net/qq_16064871/article/details/79038083
 */
public class GpsAnalysis {
    List<Byte> gps_data = new ArrayList<Byte>();
    private List<GpsSatelliteInfo> satelliteList = new ArrayList<GpsSatelliteInfo>();
    private List<GpsSatelliteInfo> GPSList = new ArrayList<GpsSatelliteInfo>();
    private List<GpsSatelliteInfo> BDList = new ArrayList<GpsSatelliteInfo>();
    private List<GpsSatelliteInfo> GLONASSList = new ArrayList<GpsSatelliteInfo>();
    private boolean bUsedGPS;
    private boolean bUsedGlonass;
    private boolean bUsedBDS;
    private boolean bUsedGalileo;

    public boolean m_bUpdating;
    int GSVIndex = 0;
    private double hrms, vrms, rms;
    private double SigmaEast, SigmaNorth;
    private long Time;// 当前时间 与(2009,1,1,0,0,0,0)的时间差
    private double deltTime;// 当前时间 与上一历元的时间差
    private Date utcDate;
    private Date gpsDate;
    private Date BJDate;//UT+8北京时间
    private String strGsa = "";// 用于保存GSA里面的卫星数据
    private int year, month, day;
    private double pdop;//PDOP综合位置精度因子
    private double hdop;//HDOP水平精度因子（为纬度和经度等误差平方和的开根号值）
    private double vdop;//VDOP垂直精度因子
    private int age;// 差分龄
    private int m_tSatNum, m_SatNum;// m_tSatNum：可见卫星数；
    private double dUndulation;// 大地水准面与WGS84之间的高程异常
    private int gpsStatue;// GPS状态，0：无效定位（正在搜索卫星...）；1：单点定位；2：差分定位；3：浮动；4：固定；5：超宽巷模糊度的固定；
    public static long m_TimeStamp = 0;
    private double fSpeed_M_S;// 速度 ,m/s
    private double heading;// 方位，角度格式：弧度
    private volatile static GpsAnalysis gpsAnalysis = null;
    //经纬度
    private double latitude = 0;//维度
    private double longitude = 0;//经度
    private double altitude = 0;

    public static GpsAnalysis getInstance() {
        synchronized (GpsAnalysis.class) {
            if(gpsAnalysis == null)
                gpsAnalysis =new GpsAnalysis();
        }
        return gpsAnalysis;
    }

    private void logUtil(String s){
        Log.i("gps",s);
    }

    private boolean checkNMEAData(String strData) {
        int nCheckSum, index = -1;
        String str, str0;
        if (!strData.contains("*")) {
            return false;
        }
        if (!strData.contains("$"))
            return false;
        if (strData.indexOf("$") > strData.indexOf("*"))
            return false;
        String stem = (strData.substring(0, strData.indexOf("*")));
        byte[] cData = stem.getBytes();
        nCheckSum = cData[1];
        for (int n = 2; n < cData.length; n++)// 值校验
        {
            nCheckSum ^= cData[n];
        }
        str = String.format(("%02x"), nCheckSum);
        index = strData.indexOf("*");
        if (index == -1)
            return false;
        if (strData.length() < index + 3)
            return false;
        str0 = strData.substring(index + 1, index + 3);
        if (str.equalsIgnoreCase(str0)) {
            return true;
        } else {
            Log.e("CheckNmea", strData);
            Log.e("计算校验值", str);
            return false;
        }
    }

    public static int stringNumbers(String str, String specter) {
        int count = 0;
        char c = specter.charAt(0);
        for (int i = 0; i < str.length(); i++) {
            char tmp = str.charAt(i);
            if (Character.valueOf(c).equals(tmp)) {
                count += 1;
            }
        }
        return count;
    }

    @SuppressLint("DefaultLocale")
    public void processNmeaData(String nmea) {
        if (nmea.length() == 0)
            return;
        if (!checkNMEAData(nmea)) {
            // 可能是A318的命令返回值

            return;
        }
        // Log.e("NMEA完整语句", nmea);
        if (!nmea.startsWith("$GPPWR,") && !nmea.startsWith("$GNGST,")
                && !nmea.startsWith("$GPGST,") && !nmea.startsWith("$GLGSV,")
                && !nmea.startsWith("$GNGSV,") && !nmea.startsWith("$BDGSV,")
                && !nmea.startsWith("$GPZDA,") && !nmea.startsWith("$GPGSA,")
                && !nmea.startsWith("$GNVTG,") && !nmea.startsWith("$GPVTG,")
                && !nmea.startsWith("$GNGSA,") && !nmea.startsWith("$GPNTR,")
                && !nmea.startsWith("$GNGGA,") && !nmea.startsWith("$GPGGA,")
                && !nmea.startsWith("$GPRMC,") && !nmea.startsWith("$GPGSV,")
                && !nmea.startsWith("$BDGSA,"))
            return;
        String[] sField = nmea.split(",");
        int iFieldNum = stringNumbers(nmea, ",");
        if (sField == null)
            return;
        if ((sField[0].equalsIgnoreCase("$GPGGA") || sField[0]
                .equalsIgnoreCase("$GNGGA")) && iFieldNum >= 14) {//输出GPS的定位信息
            logUtil("NMEA完整语句:"+nmea);
            if (sField[6].trim().length() > 0) {
                String gpsS = "";
                switch (Integer.parseInt(sField[6])) {
                    case 0:// 无效定位
                        gpsStatue = 0;
                        gpsS = "GPGGA无效定位";
                        break;
                    case 1:// 单点
                        gpsStatue = 1;
                        gpsS = "单点";
                        break;
                    case 2:// 差分
                        gpsStatue = 2;
                        gpsS = "查分";
                        break;
                    case 3:
                    case 4:// 固定
                    case 8:
                        gpsStatue = 4;
                        gpsS = "固定";
                        break;
                    case 5:// 浮动
                        gpsStatue = 3;
                        gpsS = "浮动";
                        break;
                }
                logUtil("GPS状态: "+gpsS);
                if (sField[2].trim().length() > 3) {
                    latitude = Double.parseDouble(sField[2].substring(0, 2))
                            + Double.parseDouble(sField[2].substring(2)) / 60;
                }
                if (sField[3].equals("S"))
                    latitude *= -1.0;
                if (sField[4].trim().length() > 4) {
                    longitude = Double.parseDouble(sField[4].substring(0, 3))
                            + Double.parseDouble(sField[4].substring(3)) / 60;
                }
                if (sField[5].equals("W")) {
                    longitude *= -1.0;
                    longitude += 360;
                }
                if (sField[7].trim().length() > 0) {
                    m_SatNum = Integer.parseInt(sField[7]);
                } else {
                    m_SatNum = 0;
                }
                if (sField[9].trim().length() > 0) {
                    if (sField[11].trim().length() == 0)
                        sField[11] = "0";
                    altitude = Double.parseDouble(sField[9]) + Double.parseDouble(sField[11]);
                    dUndulation = Double.parseDouble(sField[11]);
                }
            }
            if (sField[8].trim().length() > 0){
                hdop = Double.parseDouble(sField[8]);
                logUtil("水平精确度："+hdop);
            }
            if (sField[13].trim().length() > 0) {
                age = Integer.parseInt(sField[13]);
            } else {
                age = 99;
            }

            int m_Sec = 1, iSecOff = 0;
            int m_Hour = 1, m_Min = 1;
            if (sField[1].trim().length() >= 6) {
                m_Hour = Integer.parseInt(sField[1].substring(0, 2));
                m_Min = Integer.parseInt(sField[1].substring(2, 4));
                m_Sec = Integer.parseInt(sField[1].substring(4, 6));
            }
            if (m_Sec > 59) {
                iSecOff = m_Sec - 59;
                m_Sec = 59;
            }
            if (m_Hour < 0 || m_Hour > 23 || m_Min < 0 || m_Min > 59
                    || iSecOff > 60) {
                return;
            }
            String sDate = String.format("%04d-%02d-%02d %02d:%02d:%02d", year,
                    month, day, m_Hour, m_Min, m_Sec);
            BJDate=utc2BJDate(sDate);
//            gpsDate = DateUtil.GetLocalDate(DateUtil.parseDate(sDate));
//            Time = DateUtil.GetGPSTimeSpan(gpsDate) / 1000;
            deltTime = 1;

            m_TimeStamp = Time;
            assert BJDate != null;
            logUtil(BJDate.toString());
            logUtil("纬经度 "+latitude+","+longitude);
            logUtil("经纬度 "+longitude+","+latitude);
        } else if ((sField[0].equalsIgnoreCase("$GPGSA")
                || sField[0].equalsIgnoreCase("$GNGSA") || sField[0]
                .equalsIgnoreCase("$BDGSA")) && iFieldNum >= 17) {//卫星DOP值信息

            if (strGsa.trim().equals("")) {
                if (nmea.indexOf(",,") >= 11)
                    strGsa = nmea.substring(11, nmea.indexOf(",,"));
            } else {
                if (nmea.indexOf(",,") >= 11)
                    strGsa = String.format("%s,%s", strGsa,
                            nmea.substring(11, nmea.indexOf(",,")));
            }
            if (sField[15].trim().length() > 0) {
                pdop = Double.parseDouble(sField[15]);
            }
            if (sField[16].trim().length() > 0)
                hdop = Double.parseDouble(sField[16]);
            if (sField[17].trim().length() > 0 && sField[17].contains("*")) {
                sField[17] = sField[17].substring(0, sField[17].indexOf("*"));
                if (sField[17].trim().length() > 0)
                    vdop = Double.parseDouble(sField[17]);
            }
            logUtil("精度信息：pdop:"+pdop+" hdop:"+hdop+" vdop:"+vdop);
        } else if (sField[0].equalsIgnoreCase("$GPRMC") //输出GPS推荐的最短数据信息
                || sField[0].equalsIgnoreCase("$GNRMC")
                || sField[0].equalsIgnoreCase("$GLRMC")
                || sField[0].equalsIgnoreCase("$BDRMC")) {
            logUtil("NMEA完整语句:"+nmea);
            if (sField[7].trim().length() > 0) {//地面速度
                fSpeed_M_S = Double.parseDouble(sField[7]) * 1.852 / 3600 * 1000;
            }
            if (sField[7].trim().length() > 0) {
                if (Double.parseDouble(sField[7]) > 0.05) {
                    if (sField[8].trim().length() > 0)
                        heading = Double.parseDouble(sField[8]);
                }
            }
            long m_Year = 1900, m_Month = 1, m_Day = 1;
            if (sField[9].trim().length() > 5) {//日期信息
                m_Year = Integer.parseInt(sField[9].substring(4));
                m_Month = Integer.parseInt(sField[9].substring(2, 4));
                m_Day = Integer.parseInt(sField[9].substring(0, 2));
            }
            if (m_Year >= 89)
                m_Year += 1900;
            else
                m_Year += 2000;
            if (m_Year < 1990 || m_Year > 2099 || m_Month < 1 || m_Month > 12
                    || m_Day < 1 || m_Day > 31) {
                m_Year = m_Month = m_Day = 0;
                return;
            }

            int m_Sec = 0, iSecOff = 0;
            int m_Hour = 1, m_Min = 1;
            if (sField[1].trim().length() >= 6) {
                m_Hour = Integer.parseInt(sField[1].substring(0, 2));
                m_Min = Integer.parseInt(sField[1].substring(2, 4));
                m_Sec = Integer.parseInt(sField[1].substring(4, 6));
            }
            if (m_Sec > 59) {
                iSecOff = m_Sec - 59;
                m_Sec = 59;
            }
            if (m_Hour < 0 || m_Hour > 23 || m_Min < 0 || m_Min > 59
                    || iSecOff > 60) {
                return;
            }
            String sDate = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                    m_Year, m_Month, m_Day, m_Hour, m_Min, m_Sec);
            // Log.e("开始时间", sDate);
            year = (int) m_Year;
            month = (int) m_Month;
            day = (int) m_Day;

            //解析经纬度
            if (sField[2].equals("A")){
                logUtil("GPRMC有效定位");
            }
            if (sField[2].equals("V")){
                logUtil("GPRMC无效定位");
            }
            if (sField[3].trim().length() > 3) {
                latitude = Double.parseDouble(sField[3].substring(0, 2))
                        + Double.parseDouble(sField[3].substring(2)) / 60;
            }
            if (sField[4].equals("S")){
                latitude *= -1.0;
            }
            if (sField[5].trim().length() > 4) {
                longitude = Double.parseDouble(sField[5].substring(0, 3))
                        + Double.parseDouble(sField[5].substring(3)) / 60;
            }
            if (sField[6].equals("W")) {
                longitude *= -1.0;
                longitude += 360;
            }
//            gpsDate = DateUtil.GetLocalDate(DateUtil.parseDate(sDate));
            BJDate=utc2BJDate(sDate);
            m_TimeStamp = Time;
            assert BJDate != null;
            logUtil(BJDate.toString());
            logUtil("纬经度 "+latitude+","+longitude);
            logUtil("经纬度 "+longitude+","+latitude);
        } else if (sField[0].equalsIgnoreCase("$GPZDA") && iFieldNum >= 5) {//当前时间（UTC）信息
            long m_Year = 1900, m_Month = 1, m_Day = 1;
            if (sField[4].trim().length() > 0) {
                m_Year = Integer.parseInt(sField[4]);
            }
            if (sField[3].trim().length() > 0) {
                m_Month = Integer.parseInt(sField[3]);
            }
            if (sField[4].trim().length() > 0) {
                m_Day = Integer.parseInt(sField[2]);
            }

            if (m_Year < 1990 || m_Year > 2099 || m_Month < 1 || m_Month > 12
                    || m_Day < 1 || m_Day > 31) {
                return;
            }
            int m_Sec = 1, iSecOff = 0;
            int m_Hour = 1, m_Min = 1;
            if (sField[1].trim().length() >= 6) {
                m_Hour = Integer.parseInt(sField[1].substring(0, 2));
                m_Min = Integer.parseInt(sField[1].substring(2, 4));
                m_Sec = Integer.parseInt(sField[1].substring(4, 6));
            }
            if (m_Sec > 59) {
                iSecOff = m_Sec - 59;
                m_Sec = 59;
            }
            if (m_Hour < 0 || m_Hour > 23 || m_Min < 0 || m_Min > 59
                    || iSecOff > 60) {
                return;
            }
            // Log.e("开始时间", sDate);
            year = (int) m_Year;
            month = (int) m_Month;
            day = (int) m_Day;

            String sDate = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                    m_Year, m_Month, m_Day, m_Hour, m_Min, m_Sec);
//            gpsDate = DateUtil.GetLocalDate(DateUtil.parseDate(sDate));
//            utcDate = (new Date(sDate));
//            Time = DateUtil.GetGPSTimeSpan(gpsDate) / 1000;
            BJDate=utc2BJDate(sDate);
            m_TimeStamp = Time;
        } else if (sField[0].equalsIgnoreCase("$GPVTG")
                || sField[0].equalsIgnoreCase("$GNVTG")
                || sField[0].equalsIgnoreCase("$GLVTG")
                || sField[0].equalsIgnoreCase("$BDVTG")) {//地面速度信息
            if (sField[8].trim().equals("K")) {
                fSpeed_M_S = Double.parseDouble(sField[7]) * 1000 / 3600;
            }
            if (sField[7].trim().length() > 0) {
                if (Double.parseDouble(sField[7]) > 0.05) {
                    if (sField[1].trim().length() > 0)
                        heading = Double.parseDouble(sField[1]);
                }
            }
        } else if (sField[0].equalsIgnoreCase("$GPGSV")
                || sField[0].equalsIgnoreCase("$GNGSV")) {//可见卫星信息
            if (Integer.parseInt(sField[2]) == 1) {
                GSVIndex = 1;
                m_tSatNum = 0;
                GPSList.clear();
                satelliteList.clear();
                m_bUpdating = true;
                if (BDList.size() > 0) {
                    bUsedBDS = true;
                    for (int i = 0; i < BDList.size(); i++) {
                        GpsSatelliteInfo sGPS = BDList.get(i);
                        satelliteList.add(sGPS);
                        m_tSatNum++;
                    }
                } else {
                    bUsedBDS = false;
                }
                if (GLONASSList.size() > 0) {
                    bUsedGlonass = true;
                    for (int i = 0; i < GLONASSList.size(); i++) {
                        GpsSatelliteInfo sGPS = GLONASSList.get(i);
                        satelliteList.add(sGPS);
                        m_tSatNum++;
                    }
                } else {
                    bUsedGlonass = false;
                }
            } else
                GSVIndex++;
            if (Integer.parseInt(sField[2]) == GSVIndex) {
                int rec;
                for (int i = 0; i < 4; i++) {
                    rec = i * 4 + 4;
                    if (rec < iFieldNum) {
                        if (sField[rec + 3].contains("*")) {
                            sField[rec + 3] = (sField[rec + 3].substring(0,
                                    sField[rec + 3].indexOf("*")));//
                        }
                        if (rec + 3 <= iFieldNum
                                && (!sField[rec].equalsIgnoreCase(""))
                                && (!sField[rec + 1].equalsIgnoreCase(""))
                                && (!sField[rec + 3].equalsIgnoreCase(""))) {
                            GpsSatelliteInfo sGPS = new GpsSatelliteInfo();
                            sGPS.setAzimuth(Short.parseShort(sField[rec + 2]));
                            if (sField[rec + 1].contains("."))
                                sField[rec + 1] = sField[rec + 1].substring(0,
                                        sField[rec + 1].indexOf("."));
                            sGPS.setElevation(Integer.parseInt(sField[rec + 1]));
                            sGPS.setPrn(Integer.parseInt(sField[rec]));
                            if (sField[rec + 3].contains(".")) {
                                sField[rec + 3] = sField[rec + 3].substring(0,
                                        sField[rec + 3].indexOf("."));
                                sGPS.setSnrL1(Integer.parseInt(sField[rec + 3]));
                            } else {
                                sGPS.setSnrL1(Integer.parseInt(sField[rec + 3]));//
                            }
                            sGPS.setbUsed(strGsa.contains(sField[rec]));
                            GPSList.add(sGPS);
                            satelliteList.add(sGPS);
                            // m_SatNum++;
                            m_tSatNum++;
//                            logUtil("方位角:"+sGPS.getAzimuth()+" 仰角："+sGPS.getElevation()+" SVID:"+sGPS.getPrn()+" 信噪比"+sGPS.getSnrL1()+" isbUsed:"+sGPS.isbUsed());
                        }
                        // m_tSatNum++;
                    }
                }
            }
            if (Integer.parseInt(sField[1]) == GSVIndex) {
                m_bUpdating = false;
            }
//            logUtil("m_SatNum可见卫星数："+m_SatNum);
//            logUtil("m_tSatNum可见卫星数："+m_tSatNum);
        } else if (sField[0].equalsIgnoreCase("$GLGSV")) {//可见卫星信息,用于GLONASS卫星
            if (Integer.parseInt(sField[2]) == 1) {
                GLONASSList.clear();
                GSVIndex = 1;
                m_bUpdating = true;
            } else
                GSVIndex++;

            if (Integer.parseInt(sField[2]) == GSVIndex) {
                int rec;
                for (int i = 0; i < 4; i++) {
                    rec = i * 4 + 4;
                    if (rec + 3 <= iFieldNum) {
                        if (sField[rec + 3].contains("*")) {
                            sField[rec + 3] = (sField[rec + 3].substring(0,
                                    sField[rec + 3].indexOf("*")));//
                        }
                        if (sField[rec + 3].contains("*")) {
                            sField[rec + 3] = (sField[rec + 3].substring(0,
                                    sField[rec + 3].indexOf("*")));//
                        }
                        if ((!sField[rec].equalsIgnoreCase(""))
                                && (!sField[rec + 1].equalsIgnoreCase(""))
                                && (!sField[rec + 3].equalsIgnoreCase(""))) {
                            GpsSatelliteInfo sGPS = new GpsSatelliteInfo();
                            sGPS.setAzimuth(Short.parseShort(sField[rec + 2]));
                            if (sField[rec + 1].contains("."))
                                sField[rec + 1] = sField[rec + 1].substring(0,
                                        sField[rec + 1].indexOf("."));
                            sGPS.setElevation(Integer.parseInt(sField[rec + 1]));
                            sGPS.setPrn(Integer.parseInt(sField[rec]));
                            if (sField[rec + 3].contains(".")) {
                                sField[rec + 3] = sField[rec + 3].substring(0,
                                        sField[rec + 3].indexOf("."));
                                sGPS.setSnrL1(Integer.parseInt(sField[rec + 3]));
                            } else {
                                sGPS.setSnrL1(Integer.parseInt(sField[rec + 3]));//
                            }
                            // sGPS.setSnrL1(Integer.parseInt(sField[rec +
                            // 3]));//
                            sGPS.setbUsed(strGsa.contains(sField[rec]));
                            GLONASSList.add(sGPS);

                            if (!bUsedGlonass) {
                                satelliteList.add(sGPS);
                            }
                        }
                    }
                }
            }
            if (Integer.parseInt(sField[1]) == GSVIndex) {
                m_bUpdating = false;
            }
        } else if (sField[0].equalsIgnoreCase("$BDGSV")) {//可见卫星信息,北斗卫星
            if (Integer.parseInt(sField[2]) == 1) {
                BDList.clear();
                GSVIndex = 1;
                m_bUpdating = true;
            } else
                GSVIndex++;

            if (Integer.parseInt(sField[2]) == GSVIndex) {
                int rec;
                for (int i = 0; i < 4; i++) {
                    rec = i * 4 + 4;
                    if (rec + 3 <= iFieldNum) {
                        if (sField[rec + 3].contains("*")) {
                            sField[rec + 3] = (sField[rec + 3].substring(0,
                                    sField[rec + 3].indexOf("*")));//
                        }
                        if ((!sField[rec].equalsIgnoreCase(""))
                                && (!sField[rec + 1].equalsIgnoreCase(""))
                                && (!sField[rec + 3].equalsIgnoreCase(""))) {
                            GpsSatelliteInfo sGPS = new GpsSatelliteInfo();
                            sGPS.setAzimuth(Short.parseShort(sField[rec + 2]));
                            if (sField[rec + 1].contains("."))
                                sField[rec + 1] = sField[rec + 1].substring(0,
                                        sField[rec + 1].indexOf("."));
                            sGPS.setElevation(Integer.parseInt(sField[rec + 1]));
                            sGPS.setPrn((Integer.parseInt(sField[rec])));
                            if (sField[rec + 3].contains(".")) {
                                sField[rec + 3] = sField[rec + 3].substring(0,
                                        sField[rec + 3].indexOf("."));
                                sGPS.setSnrL1(Integer.parseInt(sField[rec + 3]));
                            } else {
                                sGPS.setSnrL1(Integer.parseInt(sField[rec + 3]));//
                            }
                            sGPS.setbUsed(strGsa.contains(sField[rec]));
                            BDList.add(sGPS);
                            // m_SatNum++;
                            // m_tSatNum++;
                            if (!bUsedBDS) {
                                satelliteList.add(sGPS);
                            }
                        }
                        // m_tSatNum++;
                    }
                }
            }
            if (Integer.parseInt(sField[1]) == GSVIndex) {
                m_bUpdating = false;
            }
        } else if (((sField[0].equalsIgnoreCase("$GPGST")) || (sField[0]//定位标准差信息
                .equalsIgnoreCase("$GNGST"))) && iFieldNum >= 8) {

            if (sField[7].trim().length() > 0) {
                SigmaEast = Double.parseDouble(sField[7]);
            }
            if (sField[6].trim().length() > 0) {
                SigmaNorth = Double.parseDouble(sField[6]);
            }
            if (sField[8].contains("*")) {
                sField[8] = (sField[8].substring(0, sField[8].indexOf("*")));//
            }
            if (sField[8].trim().length() > 0) {
                vrms = Double.parseDouble(sField[8]);
            }
            hrms = Math.sqrt(SigmaEast * SigmaEast + SigmaNorth * SigmaNorth);
            rms = Math.sqrt(hrms * hrms + vrms * vrms);
        }
    }

    public double getVdop() {
        return vdop;
    }

    public double getHdop() {
        return hdop;
    }

    public double getPdop() {
        return pdop;
    }

    public double getHrms() {
        return hrms;
    }

    public double getVrms() {
        return vrms;
    }

    //纬度
    public double getLatitude() {
        return latitude;
    }

    //经度
    public double getLongitude() {
        return longitude;
    }

    //椭球高
    public double getAltitude() {
        return altitude;
    }

    //解状态
    public int getStatusType() {
        return gpsStatue;
    }

    //速度
    public double getSpeed() {
        return fSpeed_M_S;
    }

    //方位角 航向角
    public double getBearing() {
        return heading;
    }

    //可见卫星
    public int getVisibleGnssCount() {
        return m_tSatNum;
    }

    //可视卫星
    public int getLockGnssCount() {
        return m_SatNum;
    }

    //差分龄
    public int getAge() {
        return age;
    }

    public Date getLocalTime() {
        return gpsDate;
    }

    public Date getUtcTime() {
        return utcDate;
    }

    public List<GpsSatelliteInfo> getSatelliteList(){
        return satelliteList;
    }

    private class GpsSatelliteInfo {

        private short Azimuth;//方位角
        private int Elevation;//高度角
        private int Prn;//SVID 卫星编号
        private int SnrL1;//信噪比
        private boolean bUsed;

        public short getAzimuth() {
            return Azimuth;
        }

        public void setAzimuth(short azimuth) {
            Azimuth = azimuth;
        }

        public int getElevation() {
            return Elevation;
        }

        public void setElevation(int elevation) {
            Elevation = elevation;
        }

        public int getPrn() {
            return Prn;
        }

        public void setPrn(int prn) {
            Prn = prn;
        }

        public int getSnrL1() {
            return SnrL1;
        }

        public void setSnrL1(int snrL1) {
            SnrL1 = snrL1;
        }

        public boolean isbUsed() {
            return bUsed;
        }

        public void setbUsed(boolean bUsed) {
            this.bUsed = bUsed;
        }
    }
    //UTC转北京时间
    private Date utc2BJDate(String date){
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(formatter.parse(date));
            calendar.set(Calendar.HOUR,calendar.get(Calendar.HOUR)+8);
            return calendar.getTime();
        }catch (Exception e){
            return null;
        }
    }
}
