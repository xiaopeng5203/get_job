package boss;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class BossEnum {
    @Getter
    public enum Experience {
        NULL("不限", "0"),
        STUDENT("在校生", "108"),
        GRADUATE("应届毕业生", "102"),
        UNLIMITED("经验不限", "101"),
        LESS_THAN_ONE_YEAR("1年以下", "103"),
        ONE_TO_THREE_YEARS("1-3年", "104"),
        THREE_TO_FIVE_YEARS("3-5年", "105"),
        FIVE_TO_TEN_YEARS("5-10年", "106"),
        MORE_THAN_TEN_YEARS("10年以上", "107");

        private final String name;
        private final String code;

        Experience(String name, String code) {
            this.name = name;
            this.code = code;
        }

        public static Optional<String> getCode(String name) {
            return Arrays.stream(Experience.values()).filter(experience -> experience.name.equals(name)).findFirst().map(experience -> experience.code);
        }

        @JsonCreator
        public static Experience forValue(String value) {
            for (Experience experience : Experience.values()) {
                if (experience.name.equals(value)) {
                    return experience;
                }
            }
            return NULL;
        }
    }

    @Getter
    public enum CityCode {
        NULL("不限", "0"),
        ALL("全国", "100010000"),
        ANSHAN("鞍山", "101070300"),
        ALASHAN_MENG("阿拉善盟", "101081200"),
        ANKANG("安康", "101110700"),
        AKESU("阿克苏地区", "101131000"),
        ALETAI_DIQU("阿勒泰地区", "101131500"),
        ALAER("阿拉尔", "101131700"),
        ALI_DIQU("阿里地区", "101140700"),
        ANYANG("安阳", "101180200"),
        ANQING("安庆", "101220600"),
        ANSHUN("安顺", "101260300"),
        ABA("阿坝藏族羌族自治州", "101271900"),
        MACAU("澳门", "101330100"),
        BEIJING("北京", "101010100"),
        BAICHENG("白城", "101060500"),
        BAISHAN("白山", "101060800"),
        BAOTOU("包头", "101080200"),
        BAYAN_NAOER("巴彦淖尔", "101080800"),
        BAODING("保定", "101090200"),
        BAOJI("宝鸡", "101110900"),
        BINZHOU("滨州", "101121100"),
        BAYINGUOLENG("巴音郭楞蒙古自治州", "101130400"),
        BOERTALA("博尔塔拉蒙古自治州", "101130500"),
        BEITUN("北屯市", "101132100"),
        BAIYANG("白杨市", "101132700"),
        BAIYIN("白银", "101161000"),
        BENGBU("蚌埠", "101220200"),
        BOZHOU("亳州", "101220900"),
        BIJIE("毕节", "101260500"),
        BAZHONG("巴中", "101270900"),
        BAOSHAN("保山", "101290300"),
        BAISE("百色", "101301000"),
        BEIHAI("北海", "101301300"),
        BAISHA("白沙黎族自治县", "101311400"),
        BAOTING("保亭黎族苗族自治县", "101311800"),
        CHONGQING("重庆", "101040100"),
        CHANGCHUN("长春", "101060100"),
        ZHAOYANG("朝阳", "101071200"),
        CHIFENG("赤峰", "101080500"),
        CHENGDE("承德", "101090400"),
        CANGZHOU("沧州", "101090700"),
        CHANGZHI("长治", "101100500"),
        CHANGJI("昌吉回族自治州", "101130300"),
        CHANGDU("昌都", "101140300"),
        CHANGZHOU("常州", "101191100"),
        CHUZHOU("滁州", "101221000"),
        CHIZHOU("池州", "101221500"),
        CHANGSHA("长沙", "101250100"),
        CHENZHOU("郴州", "101250500"),
        CHANGDE("常德", "101250600"),
        CHENGDU("成都", "101270100"),
        CHAOZHOU("潮州", "101281500"),
        CHUXIONG("楚雄彝族自治州", "101291700"),
        CHONGZUO("崇左", "101300200"),
        CHENGMAI("澄迈", "101311200"),
        CHANGJIANG("昌江黎族自治县", "101311500"),
        DAQING("大庆", "101050800"),
        DATONG("大同", "101100200"),
        DEZHOU("德州", "101120400"),
        DONGYING("东营", "101121200"),
        DINGXI("定西", "101160200"),
        DAZHOU("达州", "101270600"),
        DEYANG("德阳", "101271700"),
        DONGGUAN("东莞", "101281600"),
        DONGSHA("东沙群岛", "101282200"),
        DEHONG("德宏傣族景颇族自治州", "101291300"),
        DIQING("迪庆藏族自治州", "101291500"),
        DALIBAI("大理白族自治州", "101291600"),
        DANZHOU("儋州", "101310400"),
        DONGFANG("东方", "101310900"),
        DINGAN("定安", "101311000"),
        EERDUOSI("鄂尔多斯", "101080600"),
        EZHOU("鄂州", "101200300"),
        ENSHI("恩施土家族苗族自治州", "101201300"),
        FUSHUN("抚顺", "101070400"),
        FUXIN("阜新", "101070900"),
        FUYANG("阜阳", "101220800"),
        FUZHOU("福州", "101230100"),
        FUZHOU2("抚州", "101240400"),
        FOSHAN("佛山", "101280800"),
        FANGCHENGGANG("防城港", "101301400"),
        GUOLUO("果洛藏族自治州", "101150600"),
        GANNAN("甘南藏族自治州", "101161400"),
        GUYUAN("固原", "101170400"),
        GANZHOU("赣州", "101240700"),
        GUIYANG("贵阳", "101260100"),
        GUANGAN("广安", "101270800"),
        GUANGYUAN("广元", "101271800"),
        GANZI("甘孜藏族自治州", "101272100"),
        GUANGZHOU("广州", "101280100"),
        GUILIN("桂林", "101300500"),
        GUIGANG("贵港", "101300800"),
        HAERBIN("哈尔滨", "101050100"),
        HEIHE("黑河", "101050600"),
        HEGANG("鹤岗", "101051100"),
        HULUDAO("葫芦岛", "101071400"),
        HUHEHAOTE("呼和浩特", "101080100"),
        HULUNBEIER("呼伦贝尔", "101080700"),
        HENGSHUI("衡水", "101090800"),
        HANDAN("邯郸", "101091000"),
        HANZHONG("汉中", "101110800"),
        HEZE("菏泽", "101121000"),
        HAMI("哈密", "101130900"),
        HETIAN("和田地区", "101131300"),
        HUANGHE("胡杨河市", "101132600"),
        HAIDONG("海东", "101150200"),
        HAIBEI("海北藏族自治州", "101150300"),
        HUANGNAN("黄南藏族自治州", "101150400"),
        HAINAN("海南藏族自治州", "101150500"),
        HAIXI("海西蒙古族藏族自治州", "101150800"),
        HEBI("鹤壁", "101181200"),
        HUAIAN("淮安", "101190900"),
        HUANGGANG("黄冈", "101200500"),
        HUANGSHI("黄石", "101200600"),
        HANGZHOU("杭州", "101210100"),
        HUZHOU("湖州", "101210200"),
        HEFEI("合肥", "101220100"),
        HUAINAN("淮南", "101220400"),
        HUAIBEI("淮北", "101221100"),
        HUANGSHAN("黄山", "101221600"),
        HENGYANG("衡阳", "101250400"),
        HUAIHUA("怀化", "101251200"),
        HUIZHOU("惠州", "101280300"),
        HEYUAN("河源", "101281200"),
        HONGHE("红河哈尼族彝族自治州", "101291200"),
        HEZHOU("贺州", "101300700"),
        HECHI("河池", "101301200"),
        HAIKOU("海口", "101310100"),
        JIAMUSI("佳木斯", "101050400"),
        JIXI("鸡西", "101051000"),
        JILIN("吉林", "101060200"),
        JINZHOU("锦州", "101070700"),
        JINZHONG("晋中", "101100400"),
        JINCHENG("晋城", "101100600"),
        JINAN("济南", "101120100"),
        JINING("济宁", "101120700"),
        JIUQUAN("酒泉", "101160800"),
        JIAYUGUAN("嘉峪关", "101161200"),
        JIAOZUO("焦作", "101181100"),
        JIYUAN("济源", "101181800"),
        JINGZHOU("荆州", "101200800"),
        JINGMEN("荆门", "101201200"),
        JIAXING("嘉兴", "101210300"),
        JINHUA("金华", "101210900"),
        JIUJIANG("九江", "101240200"),
        JIAN("吉安", "101240600"),
        JINGDEZHEN("景德镇", "101240800"),
        JIANGMEN("江门", "101281100"),
        JIEYANG("揭阳", "101281900"),
        KELAMAYI("克拉玛依", "101130200"),
        KEZILESU("克孜勒苏柯尔克孜自治州", "101131100"),
        KASHI("喀什地区", "101131200"),
        KEKEDALA("可克达拉市", "101132200"),
        KUNYU("昆玉市", "101132300"),
        KAIFENG("开封", "101180800"),
        KUNMING("昆明", "101290100"),
        LIAOYUAN("辽源", "101060600"),
        LIAOYANG("辽阳", "101071000"),
        LANGFANG("廊坊", "101090600"),
        LINFEN("临汾", "101100700"),
        LVLIANG("吕梁", "101101100"),
        LINYI("临沂", "101120900"),
        LIAOCHENG("聊城", "101121700"),
        LHASA("拉萨", "101140100"),
        LINZHI("林芝", "101140400"),
        LANZHOU("兰州", "101160100"),
        LONGNAN("陇南", "101161100"),
        LINXIA("临夏回族自治州", "101161300"),
        LUOYANG("洛阳", "101180900"),
        LUOHE("漯河", "101181500"),
        LIANYUNGANG("连云港", "101191000"),
        LI_SHUI("丽水", "101210800"),
        LIUAN("六安", "101221400"),
        LONGYAN("龙岩", "101230700"),
        LOUDI("娄底", "101250800"),
        LIUPANSHUI("六盘水", "101260600"),
        LUZHOU("泸州", "101271000"),
        LESHAN("乐山", "101271400"),
        LIANGSHAN("凉山彝族自治州", "101272000"),
        LIN_CANG("临沧", "101290800"),
        LIJIANG("丽江", "101290900"),
        LIUZHOU("柳州", "101300300"),
        LAIBIN("来宾", "101300400"),
        LINGAO("临高", "101311300"),
        LEDONG("乐东黎族自治县", "101311600"),
        LINGSHUI("陵水黎族自治县", "101311700"),
        MUDANJIANG("牡丹江", "101050300"),
        MAANSHAN("马鞍山", "101220500"),
        MIANYANG("绵阳", "101270400"),
        MEISHAN("眉山", "101271500"),
        MEIZHOU("梅州", "101280400"),
        MAOMING("茂名", "101282000"),
        NAQU("那曲", "101140600"),
        NANYANG("南阳", "101180700"),
        NANJING("南京", "101190100"),
        NANTONG("南通", "101190500"),
        NINGBO("宁波", "101210400"),
        NINGDE("宁德", "101230300"),
        NANPING("南平", "101230900"),
        NANCHANG("南昌", "101240100"),
        NANCHONG("南充", "101270500"),
        NEIJIANG("内江", "101271200"),
        NUJIANG("怒江傈僳族自治州", "101291400"),
        NANNING("南宁", "101300100"),
        PANJIN("盘锦", "101071300"),
        PINGLIANG("平凉", "101160300"),
        PINGDINGSHAN("平顶山", "101180500"),
        PUYANG("濮阳", "101181300"),
        PUTIAN("莆田", "101230400"),
        PINGXIANG("萍乡", "101240900"),
        PANZHIHUA("攀枝花", "101270200"),
        PUER("普洱", "101290500"),
        QIQIHAER("齐齐哈尔", "101050200"),
        QITAIHE("七台河", "101050900"),
        QINHUANGDAO("秦皇岛", "101091100"),
        QINGDAO("青岛", "101120200"),
        QINGYANG("庆阳", "101160400"),
        QIANJIANG("潜江", "101201500"),
        QUZHOU("衢州", "101211000"),
        QUANZHOU("泉州", "101230500"),
        QIANDONGNAN("黔东南苗族侗族自治州", "101260700"),
        QIANNAN("黔南布依族苗族自治州", "101260800"),
        QIANXINAN("黔西南布依族苗族自治州", "101260900"),
        QINGYUAN("清远", "101281300"),
        QUJING("曲靖", "101290200"),
        QINZHOU("钦州", "101301100"),
        QIONGHAI("琼海", "101310600"),
        QIONGZHONG("琼中黎族苗族自治县", "101311900"),
        RIZHAO("日照", "101121500"),
        RIKAZE("日喀则", "101140200"),
        SHANGHAI("上海", "101020100"),
        SUIHUA("绥化", "101050500"),
        SHUANGYASHAN("双鸭山", "101051200"),
        SIPING("四平", "101060300"),
        SONGYUAN("松原", "101060700"),
        SHENYANG("沈阳", "101070100"),
        SHIJIAZHUANG("石家庄", "101090100"),
        SHUOZHOU("朔州", "101100900"),
        SHANGLUO("商洛", "101110600"),
        SHIHEZI("石河子", "101131600"),
        SHUANGHESHI("双河市", "101132400"),
        SHANNAN("山南", "101140500"),
        SHIZUISHAN("石嘴山", "101170200"),
        SHANGQIU("商丘", "101181000"),
        SANMENXIA("三门峡", "101181700"),
        SUZHOU("苏州", "101190400"),
        SUQIAN("宿迁", "101191300"),
        SHIYAN("十堰", "101201000"),
        SUIZHOU("随州", "101201100"),
        SHENNONGJIA("神农架", "101201700"),
        SHAOXING("绍兴", "101210500"),
        SUZHOU2("宿州", "101220700"),
        SANMING("三明", "101230800"),
        SHANGRAO("上饶", "101240300"),
        SHAOYANG("邵阳", "101250900"),
        SUINING("遂宁", "101270700"),
        SHAOGUAN("韶关", "101280200"),
        SHANTOU("汕头", "101280500"),
        SHENZHEN("深圳", "101280600"),
        SHANWEI("汕尾", "101282100"),
        SANYA("三亚", "101310200"),
        SANSHA("三沙", "101310300"),
        TIANJIN("天津", "101030100"),
        TONGHUA("通化", "101060400"),
        TIELING("铁岭", "101071100"),
        TONGLIAO("通辽", "101080400"),
        TANGSHAN("唐山", "101090500"),
        TAIYUAN("太原", "101100100"),
        TONGCHUAN("铜川", "101111000"),
        TAIAN("泰安", "101120800"),
        TULUFAN("吐鲁番", "101130800"),
        TACHENG("塔城地区", "101131400"),
        TUMUSHUKE("图木舒克", "101131800"),
        TIEMENGUAN("铁门关", "101132000"),
        TIANSHUI("天水", "101160900"),
        TAIZHOU("泰州", "101191200"),
        TIANMEN("天门", "101201600"),
        TAIZHOU2("台州", "101210600"),
        TONGLING("铜陵", "101221200"),
        TONGREN("铜仁", "101260400"),
        TUNCHANG("屯昌", "101311100"),
        TAIWAN("台湾", "101341100"),
        WUHAI("乌海", "101080300"),
        WULANCHABU("乌兰察布", "101080900"),
        WEINAN("渭南", "101110500"),
        WEIFANG("潍坊", "101120600"),
        WEIHAI("威海", "101121300"),
        WULUMUQI("乌鲁木齐", "101130100"),
        WUJIAQU("五家渠", "101131900"),
        WUWEI("武威", "101160500"),
        WUZHONG("吴忠", "101170300"),
        WUXI("无锡", "101190200"),
        WUHAN("武汉", "101200100"),
        WENZHOU("温州", "101210700"),
        WUHU("芜湖", "101220300"),
        WENSHAN("文山壮族苗族自治州", "101291100"),
        WUZHOU("梧州", "101300600"),
        WUZHISHAN("五指山", "101310500"),
        WENCHANG("文昌", "101310700"),
        WANNING("万宁", "101310800"),
        XILINGUOLE("锡林郭勒盟", "101081000"),
        XINGAN_MENG("兴安盟", "101081100"),
        XINGTAI("邢台", "101090900"),
        XINZHOU("忻州", "101101000"),
        XIAN("西安", "101110100"),
        XIANYANG("咸阳", "101110200"),
        XINXING("新星市", "101132500"),
        XINING("西宁", "101150100"),
        XINXIANG("新乡", "101180300"),
        XUCHANG("许昌", "101180400"),
        XINYANG("信阳", "101180600"),
        XUZHOU("徐州", "101190800"),
        XIANGYANG("襄阳", "101200200"),
        XIAOGAN("孝感", "101200400"),
        XIANNING("咸宁", "101200700"),
        XIANTAO("仙桃", "101201400"),
        XUANCHENG("宣城", "101221300"),
        XIAMEN("厦门", "101230200"),
        XINYU("新余", "101241000"),
        XIANGTAN("湘潭", "101250200"),
        XIANGXI("湘西土家族苗族自治州", "101251400"),
        XISHUANGBANNA("西双版纳傣族自治州", "101291000"),
        HONGKONG("香港", "101320300"),
        YICHUN("伊春", "101050700"),
        YANBIAN("延边朝鲜族自治州", "101060900"),
        YINGKOU("营口", "101070800"),
        YANGQUAN("阳泉", "101100300"),
        YUNCHENG("运城", "101100800"),
        YANAN("延安", "101110300"),
        YULIN("榆林", "101110400"),
        YANTAI("烟台", "101120500"),
        YILI("伊犁哈萨克自治州", "101130600"),
        YUSHU("玉树藏族自治州", "101150700"),
        YINCHUAN("银川", "101170100"),
        YANGZHOU("扬州", "101190600"),
        YANCHENG("盐城", "101190700"),
        YICHANG("宜昌", "101200900"),
        YICHUN2("宜春", "101240500"),
        YINGTAN("鹰潭", "101241100"),
        YIYANG("益阳", "101250700"),
        YUEYANG("岳阳", "101251000"),
        YONGZHOU("永州", "101251300"),
        YIBIN("宜宾", "101271100"),
        YAAN("雅安", "101271600"),
        YUNFU("云浮", "101281400"),
        YANGJIANG("阳江", "101281800"),
        YUXI("玉溪", "101290400"),
        YULIN2("玉林", "101300900"),
        ZHANGJIAKOU("张家口", "101090300"),
        ZIBO("淄博", "101120300"),
        ZAOZHUANG("枣庄", "101121400"),
        ZHANGYE("张掖", "101160700"),
        ZHONGWEI("中卫", "101170500"),
        ZHENGZHOU("郑州", "101180100"),
        ZHOUKOU("周口", "101181400"),
        ZHUMADIAN("驻马店", "101181600"),
        ZHENJIANG("镇江", "101190300"),
        ZHOUSHAN("舟山", "101211100"),
        ZHANGZHOU("漳州", "101230600"),
        ZHUZHOU("株洲", "101250300"),
        ZHANGJIAJIE("张家界", "101251100"),
        ZUNYI("遵义", "101260200"),
        ZIGONG("自贡", "101270300"),
        ZIYANG("资阳", "101271300"),
        ZHUHAI("珠海", "101280700"),
        ZHAOQING("肇庆", "101280900"),
        ZHANJIANG("湛江", "101281000"),
        ZHONGSHAN("中山", "101281700"),
        ZHAOTONG("昭通", "101290700");

        private final String name;
        private final String code;

        CityCode(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static CityCode forValue(String value) {
            for (CityCode cityCode : CityCode.values()) {
                if (cityCode.name.equals(value)) {
                    return cityCode;
                }
            }
            return NULL;
        }

    }

    @Getter
    public enum JobType {
        NULL("不限", "0"),
        FULL_TIME("全职", "1901"),
        PART_TIME("兼职", "1903");

        private final String name;
        private final String code;

        JobType(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static JobType forValue(String value) {
            for (JobType jobType : JobType.values()) {
                if (jobType.name.equals(value)) {
                    return jobType;
                }
            }
            return NULL;
        }
    }

    @Getter
    public enum Salary {
        NULL("不限", "0"),
        BELOW_3K("3K以下", "402"),
        FROM_3K_TO_5K("3-5K", "403"),
        FROM_5K_TO_10K("5-10K", "404"),
        FROM_10K_TO_20K("10-20K", "405"),
        FROM_20K_TO_50K("20-50K", "406"),
        ABOVE_50K("50K以上", "407");

        private final String name;
        private final String code;

        Salary(String name, String code) {
            this.name = name;
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        @JsonCreator
        public static Salary forValue(String value) {
            for (Salary salary : Salary.values()) {
                if (salary.name.equals(value)) {
                    return salary;
                }
            }
            return NULL;
        }
    }

    @Getter
    public enum Degree {
        NULL("不限", "0"),
        BELOW_JUNIOR_HIGH_SCHOOL("初中及以下", "209"),
        SECONDARY_VOCATIONAL("中专/中技", "208"),
        HIGH_SCHOOL("高中", "206"),
        JUNIOR_COLLEGE("大专", "202"),
        BACHELOR("本科", "203"),
        MASTER("硕士", "204"),
        DOCTOR("博士", "205");

        private final String name;
        private final String code;

        Degree(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static Degree forValue(String value) {
            for (Degree degree : Degree.values()) {
                if (degree.name.equals(value)) {
                    return degree;
                }
            }
            return NULL;
        }
    }

    @Getter
    public enum Scale {
        NULL("不限", "0"),
        ZERO_TO_TWENTY("0-20人", "301"),
        TWENTY_TO_NINETY_NINE("20-99人", "302"),
        ONE_HUNDRED_TO_FOUR_NINETY_NINE("100-499人", "303"),
        FIVE_HUNDRED_TO_NINE_NINETY_NINE("500-999人", "304"),
        ONE_THOUSAND_TO_NINE_NINE_NINE_NINE("1000-9999人", "305"),
        TEN_THOUSAND_ABOVE("10000人以上", "306");

        private final String name;
        private final String code;

        Scale(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static Scale forValue(String value) {
            for (Scale scale : Scale.values()) {
                if (scale.name.equals(value)) {
                    return scale;
                }
            }
            return NULL;
        }
    }

    @Getter
    public enum Financing {
        NULL("不限", "0"),
        UNFUNDED("未融资", "801"),
        ANGEL_ROUND("天使轮", "802"),
        A_ROUND("A轮", "803"),
        B_ROUND("B轮", "804"),
        C_ROUND("C轮", "805"),
        D_AND_ABOVE("D轮及以上", "806"),
        LISTED("已上市", "807"),
        NO_NEED("不需要融资", "808");

        private final String name;
        private final String code;

        Financing(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static Financing forValue(String value) {
            for (Financing financing : Financing.values()) {
                if (financing.name.equals(value)) {
                    return financing;
                }
            }
            return NULL;
        }
    }

    @Getter
    public enum Industry {
        NULL("不限", "0"),
        INTERNET("互联网", "100020"),
        COMPUTER_SOFTWARE("计算机软件", "100021"),
        CLOUD_COMPUTING("云计算", "100029");

        private final String name;
        private final String code;

        Industry(String name, String code) {
            this.name = name;
            this.code = code;
        }

        @JsonCreator
        public static Industry forValue(String value) {
            for (Industry industry : Industry.values()) {
                if (industry.name.equals(value)) {
                    return industry;
                }
            }
            return NULL;
        }
    }
}
