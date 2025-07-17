#pragma once
#include "../include/event.h"
#include "../include/ConnectionHandler.h"
#include <string>
#include <map>

class StompProtocol {
private:
    ConnectionHandler &connectionHandler;
    bool isLoggedIn;
    std::string currentUser;
    int receiptIdCounter;
    int subscriptionId;
    std::map<int, std::string> pendingReceipts;  // למעקב אחרי קבלות
    std::map<std::string, int> channelAndSubscribedId; // למעקב אחרי ערוצים שנרשמנו אליהם
    std::map<std::string, std::map<std::string, std::vector<Event>>> receivedReports; // צד שמאל זה הערוץ לדוגמה משטרה וצד ימין שומר לכל ערוץ מפה שהמפתחות בה זה שמות משתמשים והערך אירועים שדווחו ע"י אותו משתמש
    int sentReportsCount;
    int receivedReportsCount;
    
public:
    StompProtocol(ConnectionHandler &handler);

    bool processCommand(const std::string &command);
    void processServerResponse(const std::string &response);

    std::string buildConnectFrame(const std::string &host, const std::string &user, const std::string &pass);
    std::string buildSubscribeFrame(const std::string &channel);
    std::string buildUnsubscribeFrame(const std::string &channel);
    std::string buildReportFrame(const Event &event, const std::string &user);
    std::string buildDisconnectFrame();

    bool isLogged() const;
    void setLoggedIn(bool status);
    bool generateSummary(const std::string &channel, const std::string &user, const std::string &filename);
    std::string epoch_to_date(int epochTime);
    void handleMessageFrame(const std::string &frame);
    void handleReceiptFrame(const std::string &frame);
    void handleErrorFrame(const std::string &frame);
    bool allReportsReceived() const;
};
