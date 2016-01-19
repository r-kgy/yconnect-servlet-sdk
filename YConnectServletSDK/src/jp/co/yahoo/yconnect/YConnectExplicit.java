/**
 * The MIT License (MIT)
 *
 * Copyright (C) 2016 Yahoo Japan Corporation. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jp.co.yahoo.yconnect;

import java.net.URI;
import java.util.zip.DataFormatException;

import jp.co.yahoo.yconnect.core.api.ApiClient;
import jp.co.yahoo.yconnect.core.api.ApiClientException;
import jp.co.yahoo.yconnect.core.http.YHttpClient;
import jp.co.yahoo.yconnect.core.oauth2.AuthorizationException;
import jp.co.yahoo.yconnect.core.oauth2.AuthorizationRequestClient;
import jp.co.yahoo.yconnect.core.oauth2.BearerToken;
import jp.co.yahoo.yconnect.core.oauth2.ExplicitCallbackUriParser;
import jp.co.yahoo.yconnect.core.oauth2.OAuth2ResponseType;
import jp.co.yahoo.yconnect.core.oauth2.RefreshTokenClient;
import jp.co.yahoo.yconnect.core.oauth2.TokenClient;
import jp.co.yahoo.yconnect.core.oauth2.TokenException;
import jp.co.yahoo.yconnect.core.oidc.IdTokenDecoder;
import jp.co.yahoo.yconnect.core.oidc.IdTokenObject;
import jp.co.yahoo.yconnect.core.oidc.IdTokenVerification;
import jp.co.yahoo.yconnect.core.oidc.OIDCDisplay;
import jp.co.yahoo.yconnect.core.oidc.OIDCPrompt;
import jp.co.yahoo.yconnect.core.oidc.UserInfoClient;
import jp.co.yahoo.yconnect.core.oidc.UserInfoObject;
import jp.co.yahoo.yconnect.core.util.StringUtil;

/**
 * YConnecct Explicit(Authorization Code Flow) Class
 *
 * @author Copyright (C) 2016 Yahoo Japan Corporation. All Rights Reserved.
 *
 */
public class YConnectExplicit {

  private final static String AUTHORIZATION_ENDPOINT_URL =
      "https://auth.login.yahoo.co.jp/yconnect/v1/authorization";

  private final static String TOKEN_ENDPOINT_URL =
      "https://auth.login.yahoo.co.jp/yconnect/v1/token";

  private final static String USERINFO_ENDPOINT_URL =
      "https://userinfo.yahooapis.jp/yconnect/v1/attribute";

  private final static String ISSUER = "https://auth.login.yahoo.co.jp";

  // Default Parameters
  private String responseType = OAuth2ResponseType.CODE;
  private String display = OIDCDisplay.DEFAULT;
  private String prompt = OIDCPrompt.LOGIN;
  private String scope = null;
  private String nonce = null;

  private AuthorizationRequestClient requestClient;
  private ExplicitCallbackUriParser responsePaser;
  private String code;
  private BearerToken accessToken;
  private String idToken;
  private UserInfoObject userInfoObject;
  private IdTokenVerification idTokenVerification;

  /**
   * YConnectExplicitのコンストラクタ。
   */
  public YConnectExplicit() {}

  /**
   * 初期化メソッド。 各パラメーターを設定する。
   * 
   * @param clientId アプリケーションID
   * @param redirectUri リダイレクトURL
   * @param state リクエストとコールバック間の検証用のランダムな文字列を指定してください
   */
  public void init(String clientId, String redirectUri, String state) {
    requestClient = new AuthorizationRequestClient(AUTHORIZATION_ENDPOINT_URL, clientId);
    requestClient.setRedirectUri(redirectUri);
    requestClient.setState(state);
  }

  /**
   * 初期化メソッド。 各パラメーターを設定する。
   * 
   * @param clientId アプリケーションID
   * @param redirectUri リダイレクトURL
   * @param state リクエストとコールバック間の検証用のランダムな文字列を指定してください
   * @param responseType レスポンスタイプ（response_type）
   * @param display テンプレートの種類（display）
   * @param prompt クライアントが強制させたいアクション（prompt）
   * @param scope UserInfo APIから取得できる属性情報の指定（scope）
   * @param nonce リプレイアタック対策のランダムな文字列を指定してください
   */
  public void init(String clientId, String redirectUri, String state, String responseType,
      String display, String[] prompt, String[] scope, String nonce) {
    requestClient = new AuthorizationRequestClient(AUTHORIZATION_ENDPOINT_URL, clientId);
    requestClient.setRedirectUri(redirectUri);
    requestClient.setState(state);
    this.responseType = responseType;
    this.display = display;
    this.prompt = StringUtil.implode(prompt);
    this.scope = StringUtil.implode(scope);
    this.nonce = nonce;
  }

  /**
   * AuthorizationエンドポイントへのリクエストURIを生成する。
   * 
   * @return リクエストURI
   */
  public URI generateAuthorizationUri() {
    requestClient.setResponseType(responseType);
    requestClient.setParameter("display", display);
    requestClient.setParameter("prompt", prompt);
    if (scope != null)
      requestClient.setParameter("scope", scope);
    if (nonce != null)
      requestClient.setParameter("nonce", nonce);
    return requestClient.generateAuthorizationUri();
  }

  /**
   * コールバックURLの認可コードが付加されているか確認する。
   * 
   * @param QueryString (ex. code=abc&state=xyz)
   * @return 認可コードの有無
   * @throws AuthorizationException
   */
  public boolean hasAuthorizationCode(String query) throws AuthorizationException {
    responsePaser = new ExplicitCallbackUriParser(query);
    return responsePaser.hasAuthorizationCode();
  }

  /**
   * コールバックURLに認可コードが付加されているか確認する。
   * 
   * @param uri QueryStringを含むURIインスタンス (ex. https://example.com/callback?code=abc&state=xyz)
   * @return 認可コードの有無
   * @throws AuthorizationException
   */
  public boolean hasAuthorizationCode(URI uri) throws AuthorizationException {
    String requesrQuery = null;
    if (!uri.getQuery().toString().equals("null")) {
      requesrQuery = uri.getQuery().toString();
    }
    return hasAuthorizationCode(requesrQuery);
  }

  /**
   * 認可コードを取得する。
   * 
   * @param state 初期化時に指定したstate値
   * @return 認可コードの文字列
   * @throws AuthorizationException
   */
  public String getAuthorizationCode(String state) throws AuthorizationException {
    code = responsePaser.getAuthorizationCode(state);
    return code;
  }

  /**
   * Tokenエンドポイントにリクエストする。
   * 
   * @param code 認可コード
   * @param clientId アプリケーションID
   * @param clientSecret シークレット
   * @param redirectUri リダイレクトURL
   * @throws TokenException
   * @throws Exception
   */
  public void requestToken(String code, String clientId, String clientSecret, String redirectUri)
      throws TokenException, Exception {
    TokenClient tokenClient =
        new TokenClient(TOKEN_ENDPOINT_URL, code, redirectUri, clientId, clientSecret);
    tokenClient.fetch();
    accessToken = tokenClient.getAccessToken();
    idToken = tokenClient.getIdToken();
  }

  /**
   * アクセストークンを取得する。
   * 
   * @return アクセストークンの文字列
   */
  public String getAccessToken() {
    return accessToken.getAccessToken();
  }

  /**
   * アクセストークンの有効期限を取得する。
   * 
   * @return アクセストークンの有効期限(タイムスタンプ)
   */
  public long getAccessTokenExpiration() {
    return accessToken.getExpiration();
  }

  /**
   * リフレッシュトークンを取得する。
   * 
   * @return リフレッシュトークンの文字列
   */
  public String getRefreshToken() {
    return accessToken.getRefreshToken();
  }

  /**
   * IDトークンを取得する。
   * 
   * @return 暗号化されているIDトークンの文字列
   */
  public String getIdToken() {
    return idToken;
  }

  /**
   * 暗号化されたIDトークンを復号する。
   * 
   * @param idTokenString 取得したIDトークンの文字列
   * @return IDトークンの文字列から生成したIdTokenObjectを返却
   * @throws DataFormatException
   */
  public IdTokenObject decodeIdToken(String idTokenString) throws DataFormatException {
    IdTokenDecoder idTokenDecoder = new IdTokenDecoder(idTokenString);
    return idTokenDecoder.decode();
  }

  /**
   * トークン自体に異常があるかどうかを判定する。
   * 
   * @param nonce Authorizationリクエスト時に指定したnonce値
   * @param clientId アプリケーションID
   * @param clientSecret アプリケーションIDのシークレット
   * @param idTokenString 取得したIDトークンの文字列
   * @return IDトークン検証が正しい場合にはtrue, それ以外の場合にはfalseを返却
   * @throws DataFormatException 無効なIDトークンが指定された場合に発生します
   */
  public boolean verifyIdToken(String nonce, String clientId, String clientSecret,
      String idTokenString) throws DataFormatException {
    IdTokenDecoder idTokenDecoder = new IdTokenDecoder(idTokenString);
    IdTokenObject idTokenObject = idTokenDecoder.decode();
    this.idTokenVerification = new IdTokenVerification();
    return this.idTokenVerification.check(ISSUER, nonce, clientId, idTokenObject, clientSecret,
        idTokenString);
  }

  /**
   * IdTokenの値が一致していなかった際のエラーコードを返却する。
   * 
   * @return エラーコード
   */
  public String getIdTokenErrorMessage() {
    return idTokenVerification.getErrorMessage();
  }

  /**
   * IdTokenの値が一致していなかった際のエラー概要を返却する。
   * 
   * @return エラー概要
   */
  public String getIdTokenErrorDescriptionMessage() {
    return idTokenVerification.getErrorDescriptionMessage();
  }

  /**
   * アクセストークンを更新する。
   * 
   * @param refreshToken リフレッシュトークンの文字列
   * @param clientId アプリケーションID
   * @param clientSecret シークレット
   * @throws TokenException
   * @throws Exception
   */
  public void refreshToken(String refreshToken, String clientId, String clientSecret)
      throws TokenException, Exception {
    RefreshTokenClient refreshTokenClient =
        new RefreshTokenClient(TOKEN_ENDPOINT_URL, refreshToken, clientId, clientSecret);
    refreshTokenClient.fetch();
    accessToken = refreshTokenClient.getAccessToken();
  }

  /**
   * UserInfoエンドポイントにリクエストする。
   * 
   * @param accessTokenString アクセストークンの文字列
   * @throws ApiClientException
   * @throws Exception
   */
  public void requestUserInfo(String accessTokenString) throws ApiClientException, Exception {
    UserInfoClient userInfoClient = new UserInfoClient(accessTokenString);
    userInfoClient.fetchResouce(USERINFO_ENDPOINT_URL, ApiClient.GET_METHOD);
    userInfoObject = userInfoClient.getUserInfoObject();
  }

  /**
   * UserInfoオブジェクトを取得する。
   * 
   * @return UserInfo情報のオブジェクト
   */
  public UserInfoObject getUserInfoObject() {
    return userInfoObject;
  }

  /**
   * レスポンスタイプを設定する。
   * 
   * @param responseType レスポンスタイプ（response_type）
   */
  public void setResponseType(String responseType) {
    this.responseType = responseType;
  }

  /**
   * displayを設定する。
   * 
   * @param display テンプレートの種類（display）
   */
  public void setDisplay(String display) {
    this.display = display;
  }

  /**
   * promptを設定する。
   * 
   * @param prompt クライアントが強制させたいアクション（prompt）
   */
  public void setPrompt(String[] prompt) {
    this.prompt = StringUtil.implode(prompt);
  }

  /**
   * scopeを設定する。
   * 
   * @param scope UserInfo APIから取得できる属性情報の指定（scope）
   */
  public void setScope(String[] scope) {
    this.scope = StringUtil.implode(scope);
  }

  /**
   * nonceを設定する。
   * 
   * @param nonce リプレイアタック対策のランダムな文字列を指定してください
   */
  public void setNonce(String nonce) {
    this.nonce = nonce;
  }

  /**
   * SSL証明書チェックを無効にする。
   */
  public static void disableSSLCheck() {
    YHttpClient.disableSSLCheck();
  }

  /**
   * SSL証明書チェックを有効にする。
   */
  public static void enableSSLCheck() {
    YHttpClient.enableSSLCheck();
  }

}
