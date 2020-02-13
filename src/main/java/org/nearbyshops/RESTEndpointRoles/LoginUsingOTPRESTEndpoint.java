package org.nearbyshops.RESTEndpointRoles;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.nearbyshops.DAORoles.DAOLoginUsingOTP;
import org.nearbyshops.DAORoles.DAOPhoneVerificationCodes;
import org.nearbyshops.DAORoles.DAOUserNew;
import org.nearbyshops.Globals.GlobalConstants;
import org.nearbyshops.Globals.Globals;
import org.nearbyshops.Globals.SendSMS;
import org.nearbyshops.Model.ModelRoles.PhoneVerificationCode;
import org.nearbyshops.Model.ModelRoles.ShopStaffPermissions;
import org.nearbyshops.Model.ModelRoles.StaffPermissions;
import org.nearbyshops.Model.ModelRoles.User;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.StringTokenizer;

import static org.nearbyshops.Globals.Globals.generateOTP;


@Path("/api/v1/User/LoginUsingOTP")
public class LoginUsingOTPRESTEndpoint {


    private DAOUserNew daoUser = Globals.daoUserNew;
    private DAOLoginUsingOTP daoLoginUsingOTP = new DAOLoginUsingOTP();
    private DAOPhoneVerificationCodes daoPhoneVerificationCodes = Globals.daoPhoneVerificationCodes;
    private final OkHttpClient client = new OkHttpClient();



    private static final String AUTHENTICATION_SCHEME = "Basic";




    @GET
    @Path("/LoginUsingPhoneOTP")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProfileWithLogin(@HeaderParam("Authorization")String headerParam)
    {


        final String encodedUserPassword = headerParam.replaceFirst(AUTHENTICATION_SCHEME + " ", "");

        //Decode username and password
        String usernameAndPassword = new String(Base64.getDecoder().decode(encodedUserPassword.getBytes()));

        //Split username and password tokens
        final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
        final String phone = tokenizer.nextToken();
        final String phoneOTP = tokenizer.nextToken();

        //Verifying Username and password
//        System.out.println(username);
//        System.out.println(password);


//            try {
//                Thread.sleep(3000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }




        String generatedPassword = new BigInteger(130, Globals.random).toString(32);


        User user = new User();
        user.setPassword(generatedPassword);
        user.setPhone(phone);


        boolean isOTPValid = daoPhoneVerificationCodes.checkPhoneVerificationCode(phone,phoneOTP);





        if(isOTPValid)
        {
            int rowsUpdated = daoLoginUsingOTP.upsertUserProfile(user,true);



            // get profile information and send it to user
            User userProfile = daoUser.getProfile(phone,generatedPassword);
            userProfile.setPassword(generatedPassword);
            userProfile.setPhone(phone);




            if(user.getRole() == GlobalConstants.ROLE_STAFF_CODE) {

                StaffPermissions permissions = Globals.daoStaff.getStaffPermissions(user.getUserID());

                if (permissions != null)
                {
                    user.setRt_staff_permissions(permissions);
                }
            }
            else if (user.getRole() == GlobalConstants.ROLE_SHOP_STAFF_CODE)
            {
                ShopStaffPermissions permissions = Globals.daoShopStaff.getShopStaffPermissions(user.getUserID());

                if(permissions!=null)
                {
                    user.setRt_shop_staff_permissions(permissions);
                }
            }



            if(rowsUpdated==1)
            {



//                SendSMS.sendSMS("You are logged in successfully !",
//                        user.getPhone());


                return Response.status(Response.Status.OK)
                        .entity(userProfile)
                        .build();
            }
            else
            {
                return Response.status(Response.Status.NOT_MODIFIED)
                        .build();
            }

        }
        else
        {
            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }

    }




    @PUT
    @Path("/SendPhoneVerificationCode/{phone}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendPhoneVerificationCode(@PathParam("phone")String phone)
    {

        int rowCount = 0;


        PhoneVerificationCode verificationCode = Globals.daoPhoneVerificationCodes.checkPhoneVerificationCode(phone);

        if(verificationCode==null)
        {
            // verification code not generated for this phone so generate one and send this to the user


//            BigInteger phoneCode = new BigInteger(15, Globals.random);
//            int phoneOTP = phoneCode.intValue();


//            String emailVerificationCode = new BigInteger(30, Globals.random).toString(32);



            char[] phoneOTP = generateOTP(4);

            Timestamp timestampExpiry
                    = new Timestamp(
                    System.currentTimeMillis()
                            + GlobalConstants.PHONE_OTP_EXPIRY_MINUTES *60*1000
            );


            rowCount = Globals.daoPhoneVerificationCodes.insertPhoneVerificationCode(
                    phone,String.valueOf(phoneOTP),timestampExpiry,true
            );


            if(rowCount==1)
            {
                // saved successfully

//                System.out.println("Phone Verification Code : " + phoneOTP);


                SendSMS.sendOTP(String.valueOf(phoneOTP),phone);

            }


        }
        else
        {

            // verification code already generated and has not expired so resend that same code

//            System.out.println("Phone Verification Code : " + verificationCode.getVerificationCode());


            SendSMS.sendOTP(verificationCode.getVerificationCode(),phone);


            rowCount = 1;
        }



        if(rowCount >= 1)
        {



            return Response.status(Response.Status.OK)
                    .build();
        }
        if(rowCount == 0)
        {

            return Response.status(Response.Status.NOT_MODIFIED)
                    .build();
        }

        return null;
    }





    @GET
    @Path("/CheckPhoneVerificationCode/{phone}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkPhoneVerificationCode(
            @PathParam("phone")String phone,
            @QueryParam("VerificationCode")String verificationCode
    )
    {
        // Roles allowed annotation not used for this method due to performance and efficiency requirements. Also
        // this endpoint doesnt required to be secured as it does not expose any confidential information

        boolean result = Globals.daoPhoneVerificationCodes.checkPhoneVerificationCode(phone,verificationCode);

//        System.out.println(phone);
//
//
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }


        if(result)
        {
            return Response.status(Response.Status.OK)
                    .build();

        } else
        {
            return Response.status(Response.Status.NO_CONTENT)
                    .build();
        }
    }






    @GET
    @Path("/LoginUsingGlobalCredentials")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProfileWithLogin(
            @HeaderParam("Authorization")String headerParam,
            @QueryParam("ServiceURLSDS")String serviceURLForSDS,
            @QueryParam("MarketID")int marketID,
            @QueryParam("GetServiceConfiguration")boolean getServiceConfig,
            @QueryParam("GetUserProfileGlobal")boolean getUserProfileGlobal
    ) throws IOException
    {



        boolean trusted = false;

        for(String url : GlobalConstants.trusted_market_aggregators_value)
        {

//            System.out.println("URL SDS : " + url);

            if(url.equals(serviceURLForSDS))
            {
                trusted = true;
                break;
            }
        }


        if(!trusted)
        {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .build();
        }




        final String encodedUserPassword = headerParam.replaceFirst(AUTHENTICATION_SCHEME + " ", "");

        //Decode username and password
        String usernameAndPassword = new String(Base64.getDecoder().decode(encodedUserPassword.getBytes()));

        //Split username and password tokens
        final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
        final String username = tokenizer.nextToken();
        final String password = tokenizer.nextToken();

        //Verifying Username and password
//        System.out.println(username);
//        System.out.println(password);


//            try {
//                Thread.sleep(3000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }



//        System.out.println("Username : " + phone + " | Password : " + password);



        String credentials = Credentials.basic(username, password);

        String url = serviceURLForSDS + "/api/v1/User/LoginGlobal/VerifyCredentials?GetUserProfile=true";



//        if(getUserProfileGlobal)
//        {
//            url = url + "?GetUserProfile=true";
//        }



        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", credentials)
                .build();



        User userProfileGlobal;




        try (okhttp3.Response response = client.newCall(request).execute()) {


//            if (!response.isSuccessful())
//            {
//                return Response.status(Response.Status.BAD_REQUEST)
//                        .build();
//            }

//            Headers responseHeaders = response.headers();
//            for (int i = 0; i < responseHeaders.size(); i++) {
//                System.out.println(responseHeaders.name(i) + ": " + responseHeaders.value(i));
//            }



            if(response.code()!=200)
            {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .build();
            }




//            System.out.println(response.body().string());
            userProfileGlobal = Globals.getGson().fromJson(response.body().string(),User.class);

        }






        String generatedPassword = new BigInteger(130, Globals.random).toString(32);


//        User user = new User();
//        user.setPassword(generatedPassword);
//        user.setPhone(phone);


        userProfileGlobal.setPassword(generatedPassword);




        int rowsUpdated = 0;



        if(daoLoginUsingOTP.checkUserExists(userProfileGlobal.getEmail(),userProfileGlobal.getPhone())!=null)
        {
            // user exist ...  update profile
            rowsUpdated = daoLoginUsingOTP.updateUserProfile(userProfileGlobal);
        }
        else
        {


            //             check if user account has existing associations

            if(daoLoginUsingOTP.checkUserExistsUsingAssociations(userProfileGlobal.getUserID(),serviceURLForSDS)!=null)
            {
                // account exists
                rowsUpdated = daoLoginUsingOTP.updateUserProfileAssociated(userProfileGlobal,serviceURLForSDS);

            }
            else
            {
                // user account does not exist ... insert profile
                rowsUpdated = daoLoginUsingOTP.insertUserProfile(userProfileGlobal,serviceURLForSDS,true);

            }



//            rowsUpdated = daoLoginUsingOTP.insertUserProfile(userProfileGlobal,serviceURLForSDS,true);

        }




//        int rowsUpdated = daoLoginUsingOTP.upsertUserProfileNew(userProfileGlobal,true);



        // get profile information and send it to user
        User userProfile = daoUser.getProfile(username,generatedPassword);
        userProfile.setPassword(generatedPassword);


//        userProfile.setPhone(phone);



        if(rowsUpdated==1)
        {


            if(getServiceConfig)
            {
                userProfile.setServiceConfigurationLocal(Globals.serviceConfigDAO.getServiceConfiguration(0.0,0.0));
            }

            if(getUserProfileGlobal)
            {
                userProfile.setUserProfileGlobal(userProfileGlobal);
            }


//                SendSMS.sendSMS("You are logged in successfully !",
//                        user.getPhone());

            return Response.status(Response.Status.OK)
                    .entity(userProfile)
                    .build();
        }
        else
        {
            return Response.status(Response.Status.NO_CONTENT)
                    .build();
        }
    }


}
